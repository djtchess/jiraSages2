package fr.agile.service;

import fr.agile.IssueChangelogData;
import fr.agile.dto.AvancementHistorique;
import fr.agile.model.dto.Ticket;
import fr.agile.utils.BurnupUtils;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class JiraSprintAnalysisService {

    private static final DateTimeFormatter JIRA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    @FunctionalInterface
    public interface IssueChangelogLoader {
        IssueChangelogData load(String issueKey) throws Exception;
    }

    public record SprintCommitBuckets(
            List<Ticket> committedAtStart,
            List<Ticket> addedDuring,
            List<Ticket> removedDuring) {
    }

    public boolean isDevTermineAvantSprint(IssueChangelogData changelogData, ZonedDateTime sprintStart) {
        if (changelogData == null) {
            return false;
        }

        Set<String> validStatuses = Set.of("DEV TERMINE", "INTEGRATION PR", "PAIR REVIEW", "READY TO DEMO");
        for (var entry : changelogData.getStatutAvantDateMap().entrySet()) {
            ZonedDateTime when = entry.getKey().atZone(sprintStart.getZone());
            String status = entry.getValue() == null ? "" : entry.getValue().toUpperCase();
            if (validStatuses.contains(status) && when.isBefore(sprintStart)) {
                return true;
            }
        }
        return false;
    }

    public double calculateAvancementAvantSprint(IssueChangelogData changelogData, double storyPoints, ZonedDateTime sprintStart) {
        double lastAvancementAvantSprint = 0.0;
        String statutAvant = changelogData.getLastStatusBefore(sprintStart);
        Set<String> statutsValides = Set.of("ON GOING", "PAIR REVIEW", "READY TO DEMO", "INTEGRATION PR", "DEV TERMINE");

        if (statutAvant != null && statutsValides.contains(statutAvant.toUpperCase())) {
            for (AvancementHistorique a : changelogData.getAvancementHistorique()) {
                ZonedDateTime date = BurnupUtils.parseZonedDateTime(a.getDate());

                if (date.isBefore(sprintStart) && a.getTo() != null && !a.getTo().isBlank()) {
                    try {
                        double avancement = Double.parseDouble(a.getTo());
                        if (avancement > lastAvancementAvantSprint) {
                            lastAvancementAvantSprint = avancement;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            lastAvancementAvantSprint = BurnupUtils.roundToTwoDecimals(storyPoints * (lastAvancementAvantSprint / 100.0));
        }
        return lastAvancementAvantSprint;
    }

    public SprintCommitBuckets classifyTicketsByChangelog(
            Set<Ticket> tickets,
            String sprintId,
            ZonedDateTime sprintStart,
            ZonedDateTime sprintEnd,
            IssueChangelogLoader changelogLoader) throws Exception {

        List<Ticket> committed = new ArrayList<>();
        List<Ticket> added = new ArrayList<>();
        List<Ticket> removed = new ArrayList<>();

        for (Ticket ticket : tickets) {
            IssueChangelogData changelogData = changelogLoader.load(ticket.getTicketKey());
            ticket.setChangelog(changelogData);
            if (ticket.getStoryPoints() == null) {
                ticket.setStoryPoints(0.0);
            }

            ticket.setStoryPointsRealiseAvantSprint(
                    calculateAvancementAvantSprint(changelogData, ticket.getStoryPoints(), sprintStart));
            ticket.setStatus(changelogData.getLastStatusBefore(sprintEnd));

            List<SprintChange> sprintChanges = extractSprintChanges(changelogData, sprintId, sprintStart);

            if (sprintChanges.isEmpty()) {
                fallbackAddOrCommit(ticket, sprintId, sprintStart, committed, added);
                continue;
            }

            boolean inSprintAtStart = false;
            ZonedDateTime firstAddAfterStart = null;
            ZonedDateTime firstRemoveDuring = null;

            for (SprintChange change : sprintChanges) {
                if (change.when().isAfter(sprintEnd)) {
                    break;
                }

                if (!change.when().isAfter(sprintStart)) {
                    inSprintAtStart = change.toContains();
                } else {
                    if (!change.fromContains() && change.toContains() && firstAddAfterStart == null) {
                        firstAddAfterStart = change.when();
                    }
                    if (change.fromContains() && !change.toContains() && firstRemoveDuring == null) {
                        firstRemoveDuring = change.when();
                    }
                }
            }

            if (inSprintAtStart) {
                committed.add(ticket);
            } else if (firstAddAfterStart != null) {
                added.add(ticket);
            } else {
                fallbackAddOrCommit(ticket, sprintId, sprintStart, committed, added);
            }

            if (firstRemoveDuring != null) {
                removed.add(ticket);
            }
        }

        return new SprintCommitBuckets(committed, added, removed);
    }

    private List<SprintChange> extractSprintChanges(IssueChangelogData changelogData,
                                                    String sprintId,
                                                    ZonedDateTime sprintStart) {
        List<SprintChange> sprintChanges = new ArrayList<>();

        for (AvancementHistorique history : changelogData.getSprintHistorique()) {
            if (!"Sprint".equalsIgnoreCase(history.getField())) {
                continue;
            }

            boolean fromContains = containsSprint(history.getFrom(), sprintId);
            boolean toContains = containsSprint(history.getTo(), sprintId);
            if (!fromContains && !toContains) {
                continue;
            }
            if (fromContains == toContains) {
                continue;
            }

            ZonedDateTime when = ZonedDateTime.parse(history.getDate(), JIRA_DATE_FORMATTER)
                    .withZoneSameInstant(sprintStart.getZone());
            sprintChanges.add(new SprintChange(when, fromContains, toContains));
        }

        sprintChanges.sort(Comparator.comparing(SprintChange::when));
        return sprintChanges;
    }

    private void fallbackAddOrCommit(Ticket ticket,
                                     String sprintId,
                                     ZonedDateTime sprintStart,
                                     List<Ticket> committed,
                                     List<Ticket> added) {
        if (ticket.getSprintIds().contains(sprintId)) {
            ZonedDateTime created = ticket.getCreatedDate().atStartOfDay(sprintStart.getZone());
            if (created.isAfter(sprintStart)) {
                added.add(ticket);
            } else {
                committed.add(ticket);
            }
        }
    }

    private boolean containsSprint(String raw, String sprintId) {
        if (raw == null || sprintId == null) {
            return false;
        }

        String value = raw.trim();
        if (value.equals(sprintId)) {
            return true;
        }

        value = value.replaceAll("[\\[\\]\\s]", "");
        if (value.isEmpty()) {
            return false;
        }

        for (String part : value.split(",")) {
            if (sprintId.equals(part)) {
                return true;
            }
        }
        return false;
    }

    private record SprintChange(ZonedDateTime when, boolean fromContains, boolean toContains) {
    }
}
