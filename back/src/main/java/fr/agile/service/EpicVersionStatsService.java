package fr.agile.service;

import fr.agile.JiraApiClient;
import fr.agile.dto.BoardInfo;
import fr.agile.dto.EpicVersionSprintDurationDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.model.dto.Ticket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EpicVersionStatsService {

    private static final String VERSION_INCONNUE = "Sans version";
    private static final String EPIC_INCONNU = "Sans epic";

    private final JiraApiClient jiraApiClient;

    public EpicVersionStatsService(JiraApiClient jiraApiClient) {
        this.jiraApiClient = jiraApiClient;
    }

    public List<EpicVersionSprintDurationDTO> getEpicDurationsByVersion(String projectKey) throws Exception {
        List<BoardInfo> boards = jiraApiClient.getBoardsForProject(projectKey);
        BoardInfo board = boards.stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Aucun board trouvé pour le projet " + projectKey));

        List<SprintInfoDTO> sprints = jiraApiClient.getAllSprintsForBoard(board.getId());
        Map<Long, List<Ticket>> ticketsBySprint = new HashMap<>();

        for (SprintInfoDTO sprint : sprints) {
            ticketsBySprint.put(sprint.getId(), jiraApiClient.getTicketsForSprint(projectKey, sprint.getId()));
        }

        return aggregateEpicVersionDurations(sprints, ticketsBySprint);
    }

    List<EpicVersionSprintDurationDTO> aggregateEpicVersionDurations(
            List<SprintInfoDTO> sprints,
            Map<Long, List<Ticket>> ticketsBySprint
    ) {
        Map<String, EpicVersionSprintDurationDTO> aggregation = new HashMap<>();

        for (SprintInfoDTO sprint : sprints) {
            if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
                continue;
            }

            double sprintDuration = Math.max(0, Duration.between(sprint.getStartDate(), sprint.getEndDate()).toDays());
            List<Ticket> tickets = ticketsBySprint.getOrDefault(sprint.getId(), List.of());

            for (Ticket ticket : tickets) {
                String version = safe(ticket.getVersionCorrigee(), VERSION_INCONNUE);
                String epicKey = safe(ticket.getEpicKey(), EPIC_INCONNU);
                String epicName = safe(ticket.getEpicName(), epicKey);
                double storyPoints = ticket.getStoryPoints() == null ? 0d : ticket.getStoryPoints();

                String key = version + "|" + epicKey + "|" + sprint.getId();
                aggregation.compute(key, (k, existing) -> {
                    if (existing == null) {
                        return EpicVersionSprintDurationDTO.builder()
                                .version(version)
                                .epicKey(epicKey)
                                .epicName(epicName)
                                .sprintId(sprint.getId())
                                .sprintName(sprint.getName())
                                .sprintDurationDays(sprintDuration)
                                .storyPoints(storyPoints)
                                .build();
                    }
                    existing.setStoryPoints(existing.getStoryPoints() + storyPoints);
                    return existing;
                });
            }
        }

        List<EpicVersionSprintDurationDTO> result = new ArrayList<>(aggregation.values());
        result.sort(Comparator
                .comparing(EpicVersionSprintDurationDTO::getVersion)
                .thenComparing(EpicVersionSprintDurationDTO::getEpicKey)
                .thenComparing(EpicVersionSprintDurationDTO::getSprintId));
        return result;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
