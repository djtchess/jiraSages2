package fr.agile;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.agile.dto.AvancementHistorique;
import fr.agile.dto.BoardInfo;
import fr.agile.dto.BurnupDataDTO;
import fr.agile.dto.BurnupPointDTO;
import fr.agile.dto.EpicDurationEntryDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.entities.JoursFeries;
import fr.agile.entities.SprintInfo;
import fr.agile.model.dto.Ticket;
import fr.agile.service.DevelopperService;
import fr.agile.service.EventService;
import fr.agile.service.ChangelogCacheService;
import fr.agile.service.JiraHttpClient;
import fr.agile.service.JiraParser;
import fr.agile.service.JoursFeriesService;
import fr.agile.service.JiraSprintAnalysisService;
import fr.agile.service.SprintService;
import fr.agile.sprint.SprintCapacityCalculator;
import fr.agile.utils.BurnupUtils;
import fr.agile.utils.JqlBuilder;
import fr.agile.dto.SprintVersionEpicDurationDTO;

@Component
public class JiraApiClient {

    @Value("${jira.baseUrl}")
    private String JIRA_URL;

    private static final String SEARCH_JQL_API = "/rest/api/3/search/jql";

    private static final ZoneId Z_PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter JIRA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DevelopperService developpeurService;
    @Autowired
    private EventService evenementService;
    @Autowired
    private JoursFeriesService joursFeriesService;
    @Autowired
    private SprintService sprintService;
    @Autowired
    private SprintCapacityCalculator calculator;

    private final JiraHttpClient jiraHttpClient;
    private final JiraParser jiraParser;
    private final ChangelogCacheService changelogCacheService;
    private final JiraSprintAnalysisService jiraSprintAnalysisService;

    public JiraApiClient(SprintService sprintService,
                         DevelopperService developpeurService,
                         EventService evenementService,
                         JoursFeriesService joursFeriesService,
                         SprintCapacityCalculator calculator,
                         JiraHttpClient jiraHttpClient,
                         JiraParser jiraParser,
                         ChangelogCacheService changelogCacheService,
                         JiraSprintAnalysisService jiraSprintAnalysisService) {
        this.developpeurService = developpeurService;
        this.evenementService = evenementService;
        this.joursFeriesService = joursFeriesService;
        this.calculator = calculator;
        this.sprintService = sprintService;
        this.jiraHttpClient = jiraHttpClient;
        this.jiraParser = jiraParser;
        this.changelogCacheService = changelogCacheService;
        this.jiraSprintAnalysisService = jiraSprintAnalysisService;
    }

    // =====================================================================
    // Requêtes JQL
    // =====================================================================

    /**
     * Récupère les tickets correspondant à un JQL arbitraire.
     * Migration vers POST /rest/api/3/search/jql avec pagination nextPageToken.
     */
    public List<Ticket> getTicketsParJql(String jql, ZonedDateTime sprintStartDate, boolean ticketDevAvantSprint)
            throws IOException, InterruptedException {

        System.out.println("JQL exécuté : " + jql);

        final String urlString = JIRA_URL + SEARCH_JQL_API;
        final int maxResults = 50;

        List<Ticket> ticketList = new ArrayList<>();
        String nextPageToken = null;
        boolean isLast = false;

        while (!isLast) {
            var body = jiraParser.buildSearchJqlBody(jql, maxResults, nextPageToken);
            HttpRequest request = jiraHttpClient.createPostJsonRequest(urlString, body.toString());
            String response = jiraHttpClient.sendRequest(request);
            JiraParser.TicketPage ticketPage = jiraParser.parseTicketPage(response, JIRA_URL);

            for (Ticket t : ticketPage.tickets()) {
                String key = t.getTicketKey();

                // Filtrage "Dev terminé avant le sprint"
                boolean devAvantSprint = false;
                if (ticketDevAvantSprint) {
                    try {
                        IssueChangelogData ch = getChangelogCached(key);
                        devAvantSprint = jiraSprintAnalysisService.isDevTermineAvantSprint(ch, sprintStartDate);
                    } catch (Exception e) {
                        devAvantSprint = false; // ne filtre pas si erreur changelog
                    }
                }

                if (devAvantSprint) {
                    t.setDevTermineAvantSprint(true);
                } else {
                    ticketList.add(t);
                }
            }

            isLast = ticketPage.isLast();
            nextPageToken = ticketPage.nextPageToken();
        }

        return ticketList;
    }

    // =====================================================================
    // Changelog (avec cache + vraie pagination)
    // =====================================================================

    private IssueChangelogData getChangelogCached(String issueKey) throws Exception {
        try {
            return changelogCacheService.getOrLoad(issueKey, () -> {
                try {
                    return getChangelogData(issueKey);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception nested) {
                throw nested;
            }
            throw e;
        }
    }

    public void invalidateChangelog(String issueKey) {
        changelogCacheService.evict(issueKey);
    }

    /**
     * Nouveau parse sur /rest/api/3/issue/{key}/changelog (pagination correcte)
     */
    public IssueChangelogData getChangelogData(String issueKey) throws Exception {
        Map<String, AvancementHistorique> avancementMap = new HashMap<>();
        NavigableMap<LocalDateTime, String> statusParDate = new TreeMap<>();
        List<AvancementHistorique> sprintHistories = new ArrayList<>();

        int startAt = 0;
        int maxResults = 100;
        boolean hasMore = true;

        while (hasMore) {
            String url = JIRA_URL + "/rest/api/3/issue/" + issueKey + "/changelog?startAt=" + startAt + "&maxResults=" + maxResults;
            HttpRequest request = jiraHttpClient.createRequest(url);
            String body = jiraHttpClient.sendRequest(request);

            JiraParser.ChangelogPage changelogPage = jiraParser.parseChangelogPage(body);
            avancementMap.putAll(changelogPage.data().getAvancementHistorique().stream()
                    .collect(Collectors.toMap(AvancementHistorique::getDate, a -> a, (a, b) -> b)));
            statusParDate.putAll(changelogPage.data().getStatutAvantDateMap());
            sprintHistories.addAll(changelogPage.data().getSprintHistorique());

            startAt += maxResults;
            hasMore = startAt < changelogPage.total();
        }

        List<AvancementHistorique> avancementHistorique = new ArrayList<>(avancementMap.values());
        avancementHistorique.sort(Comparator.comparing(a -> ZonedDateTime.parse(a.getDate(), JIRA_DATE_FORMATTER)));

        return new IssueChangelogData(avancementHistorique, statusParDate, sprintHistories);
    }
    // =====================================================================
    // Aide "dev terminé avant le sprint"
    // =====================================================================

    // Ancienne version (si jamais tu passes encore un JsonNode brut)
    public boolean isDevTermineAvantSprint(JsonNode changelog, ZonedDateTime sprintStart) {
        Set<String> validStatuses = Set.of("DEV TERMINE", "INTEGRATION PR", "PAIR REVIEW", "READY TO DEMO");
        for (JsonNode history : changelog.path("histories")) {
            String created = history.path("created").asText();
            ZonedDateTime changeDate;
            try {
                changeDate = ZonedDateTime.parse(created, JIRA_DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                continue;
            }
            for (JsonNode item : history.path("items")) {
                String field = item.path("field").asText(null);
                String toString = item.path("toString").asText(null);
                if ("status".equalsIgnoreCase(field)
                        && toString != null
                        && validStatuses.contains(toString.toUpperCase())
                        && changeDate.isBefore(sprintStart)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBetweenInclusiveExclusive(ZonedDateTime t, ZonedDateTime start, ZonedDateTime end) {
        return (!t.isBefore(start)) && t.isBefore(end);
    }

    // =====================================================================
    // Burnup
    // =====================================================================

    public BurnupDataDTO calculateBurnupForSprint(String sprintId) throws Exception {
        SprintInfoDTO sprintInfo = getSprintInfo(sprintId);

        Optional<SprintInfo> siOpt = sprintService.getById(Long.valueOf(sprintId));
        ZonedDateTime dateDebut =
                siOpt.map(SprintInfo::getStartDate)
                        .filter(Objects::nonNull)
                        .orElseGet(() -> sprintInfo.getStartDate());

        ZonedDateTime dateFin =
                siOpt.map(SprintInfo::getEndDate) // <-- fix ici (getEndDate)
                        .filter(Objects::nonNull)
                        .orElseGet(() -> sprintInfo.getEndDate());

        String jqlQuery = getTicketsSprint(sprintId);
        List<Ticket> tickets = getTicketsParJql(jqlQuery, dateDebut, true);

        Map<LocalDate, Double> dailyDone = new TreeMap<>();
        double totalStoryPoints = 0.0;
        double avanceAvantSprint = 0.0;

        for (Ticket ticket : tickets) {
            IssueChangelogData changelogData = getChangelogCached(ticket.getTicketKey());
            ticket.setChangelog(changelogData);
            if (ticket.getStoryPoints() == null) {
                ticket.setStoryPoints(0.0);
            }
            Double val = jiraSprintAnalysisService.calculateAvancementAvantSprint(changelogData, ticket.getStoryPoints(), dateDebut);
            avanceAvantSprint += val;
            totalStoryPoints += ticket.getStoryPoints();
            double avancemeent = ticket.getAvancement() != null ? ticket.getAvancement() : 0.0;

            System.out.println(ticket.getTicketKey() + " - " + ticket.getType() + " - " + ticket.getStatus() + " - " + ticket.getStoryPoints() + " - avancement : " + ticket.getAvancement() + "  - raf : " + (ticket.getStoryPoints() - (ticket.getStoryPoints() * (avancemeent / 100))));

            double sp = ticket.getStoryPoints() - val;
            accumulateDailyDone(dailyDone, changelogData, sp, dateDebut, dateFin);
        }

        Map<LocalDate, Double> capaciteParDate = calculateCapacity(dateDebut, dateFin, true);

        // === 1) Totaux indépendants de la vélocité
        Totaux totaux = computeTotals(dailyDone, capaciteParDate);
        double totalDone = totaux.totalDone();
        double totalJH = totaux.totalJH();

        // === 2) Sélection de la vélocité à utiliser
        double velocityToUse;
        if ("closed".equalsIgnoreCase(sprintInfo.getState()) && totalJH > 0.0) {
            double observedVelocity = BurnupUtils.roundToTwoDecimals(totalDone / totalJH);
            // persist
            sprintService.saveOrUpdateVelocity(sprintInfo.getId(), observedVelocity);
            velocityToUse = sprintService.getVelocityStartSprint(sprintInfo.getId()).orElse(0.76);
        } else if ("active".equalsIgnoreCase(sprintInfo.getState()) && totalJH > 0.0) {
            // moyenne des 5 derniers sprints closed
            velocityToUse = sprintService.getAvgVelocityLastClosed(sprintInfo.getOriginBoardId(), 5)
                    .orElse(0.76); // fallback
            sprintService.saveOrUpdateVelocityStart(sprintInfo.getId(), velocityToUse);
        } else {
            velocityToUse = sprintService.getAvgVelocityLastClosedAndActive(sprintInfo.getOriginBoardId(), 5)
                    .orElse(0.76); // fallback
            sprintService.saveOrUpdateVelocityStart(sprintInfo.getId(), velocityToUse);
        }

        // === 3) Construire les points avec la vélocité sélectionnée
        List<BurnupPointDTO> result = assembleBurnupPoints(dailyDone, capaciteParDate, velocityToUse);

        totalStoryPoints -= avanceAvantSprint;
        totalStoryPoints = BurnupUtils.roundToTwoDecimals(totalStoryPoints);

        System.out.println("totalStoryPoints : "+totalStoryPoints);
        BurnupDataDTO burnupData = new BurnupDataDTO(result, totalStoryPoints);
        return burnupData;
    }

    // Accumule les avancements journaliers dans dailyDone
    private void accumulateDailyDone(Map<LocalDate, Double> dailyDone, IssueChangelogData changelogData, double sp,
                                     ZonedDateTime dateDebut, ZonedDateTime dateFin) {
        List<AvancementHistorique> histo = changelogData.getAvancementHistorique();
        histo.sort(Comparator.comparing(a -> BurnupUtils.parseZonedDateTime(a.getDate())));

        boolean firstDate = true; // gère le cas from=100 sur la première date

        for (AvancementHistorique a : histo) {
            ZonedDateTime zdt = BurnupUtils.parseZonedDateTime(a.getDate());

            if (!zdt.isBefore(dateDebut) && !zdt.isAfter(dateFin)) {
                int valueFrom = 0;
                int valueTo = 0;
                if (a.getFrom() != null && !a.getFrom().isBlank()) {
                    valueFrom = Integer.parseInt(a.getFrom());
                }
                if (a.getTo() != null && !a.getTo().isBlank()) {
                    valueTo = Integer.parseInt(a.getTo());
                }

                int diff = valueTo - valueFrom;
                if (valueFrom == 100 && firstDate) {
                    diff = 0;
                }

                double avancement = diff / 100.0;
                sp = BurnupUtils.roundToTwoDecimals(sp);
                double value = BurnupUtils.roundToTwoDecimals(sp * avancement);

                dailyDone.merge(zdt.toLocalDate(), value, Double::sum);
                firstDate = false;
            }
        }
    }

    // Calcul de la capacité par date
    private Map<LocalDate, Double> calculateCapacity(ZonedDateTime dateDebut, ZonedDateTime dateFin, boolean withTech) {
        List<JoursFeries> joursFeries = joursFeriesService.getAll();
        List<Event> evenements = evenementService.getAll();
        List<Developper> developpeurs = developpeurService.getAll().stream()
                .filter(d -> d.getId() != 6 && d.getId() != 9)
                .toList();

        Map<LocalDate, Double> capaciteParDate = new TreeMap<>();
        for (Developper dev : developpeurs) {
            LocalDate presenceDebut = dev.getDateDebut().toLocalDate();
            LocalDate presenceFin = dev.getDateFin().toLocalDate();
            LocalDate borneDebut = presenceDebut.isAfter(dateDebut.toLocalDate()) ? presenceDebut : dateDebut.toLocalDate();
            LocalDate borneFin = presenceFin.isBefore(dateFin.toLocalDate()) ? presenceFin : dateFin.toLocalDate();

            List<SprintCapacityCalculator.JourTravail> jours =
                    calculator.calculerJoursTravailles(dev, borneDebut, borneFin, joursFeries, evenements);
            for (SprintCapacityCalculator.JourTravail jt : jours) {
                capaciteParDate.merge(jt.jour(), jt.chargeJournaliere(), Double::sum);
            }
        }
        return capaciteParDate;
    }

    private List<BurnupPointDTO> assembleBurnupPoints(Map<LocalDate, Double> dailyDone,
                                                      Map<LocalDate, Double> capaciteParDate,
                                                      double velocity) {
        List<BurnupPointDTO> result = new ArrayList<>();
        double cumulativeDone = 0.0;
        double cumulativeCapacity = 0.0;
        double cumulativeJH = 0.0;

        List<LocalDate> dates = new ArrayList<>(capaciteParDate.keySet());
        Collections.sort(dates);

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);

            // Valeurs du jour (par défaut = 0.0 si null ou absent)
            double done = BurnupUtils.roundToTwoDecimals(dailyDone.getOrDefault(date, 0.0));
            double capaciteJH = capaciteParDate.getOrDefault(date, 0.0);

            // Règle métier : ajustement -0.6 sauf dernier jour
            if (i != dates.size() - 1) {
                capaciteJH -= 0.6;
            }
            capaciteJH = Math.max(0.0, capaciteJH);

            // Capacité en points (JH * velocity global)
            double capacity = BurnupUtils.roundToTwoDecimals(capaciteJH * velocity);

            // Cumuls
            cumulativeDone = BurnupUtils.roundToTwoDecimals(cumulativeDone + done);
            cumulativeCapacity = BurnupUtils.roundToTwoDecimals(cumulativeCapacity + capacity);
            cumulativeJH = BurnupUtils.roundToTwoDecimals(cumulativeJH + capaciteJH);
            double velocityJour = BurnupUtils.roundToTwoDecimals(cumulativeDone / cumulativeJH);

            result.add(new BurnupPointDTO(date.toString(), cumulativeDone, cumulativeCapacity, cumulativeJH, velocityJour));
        }
        return result;
    }

    private record Totaux(double totalDone, double totalJH) {
    }

    private Totaux computeTotals(Map<LocalDate, Double> dailyDone, Map<LocalDate, Double> capaciteParDate) {
        double totalDone = dailyDone.values().stream().mapToDouble(Double::doubleValue).sum();
        List<LocalDate> dates = new ArrayList<>(capaciteParDate.keySet());
        Collections.sort(dates);
        double totalJH = 0.0;
        for (int i = 0; i < dates.size(); i++) {
            double jh = capaciteParDate.getOrDefault(dates.get(i), 0.0);
            if (i != dates.size() - 1) jh -= 0.6;
            totalJH += Math.max(0.0, jh);
        }
        return new Totaux(BurnupUtils.roundToTwoDecimals(totalDone), BurnupUtils.roundToTwoDecimals(totalJH));
    }

    // =====================================================================
    // Sprints / Boards
    // =====================================================================
    public SprintInfoDTO getSprintInfo(String sprintId) throws Exception {
        String urlString = JIRA_URL + "/rest/agile/1.0/sprint/" + sprintId;
        HttpRequest request = jiraHttpClient.createRequest(urlString);
        String response = jiraHttpClient.sendRequest(request);
        return jiraParser.parseSprintInfo(response, Z_PARIS);
    }


    public List<BoardInfo> getBoardsForProject(String projectKey) throws Exception {
        List<BoardInfo> boards = new ArrayList<>();
        int startAt = 0;
        int maxResults = 50;
        boolean isLast = false;

        while (!isLast) {
            String urlString = JIRA_URL + "/rest/agile/1.0/board?projectKeyOrId=" + projectKey + "&startAt=" + startAt + "&maxResults=" + maxResults;
            HttpRequest request = jiraHttpClient.createRequest(urlString);
            String response = jiraHttpClient.sendRequest(request);

            JiraParser.BoardsPage page = jiraParser.parseBoardsPage(response);
            boards.addAll(page.boards());
            isLast = page.isLast();
            startAt += maxResults;
        }
        return boards;
    }

    public List<SprintInfoDTO> getAllSprintsForBoard(long boardId) throws Exception {
        List<SprintInfoDTO> sprintList = new ArrayList<>();
        int startAt = 0;
        int maxResults = 50;

        final ZoneId zone = Z_PARIS;
        final ZonedDateTime filtreDate = ZonedDateTime.of(2024, 10, 7, 0, 0, 0, 0, zone);

        while (true) {
            String urlString = JIRA_URL + "/rest/agile/1.0/board/" + boardId
                    + "/sprint?startAt=" + startAt + "&maxResults=" + maxResults;
            HttpRequest request = jiraHttpClient.createRequest(urlString);
            String response = jiraHttpClient.sendRequest(request);

            JiraParser.SprintsPage page = jiraParser.parseSprintsPage(response, boardId, zone, filtreDate);
            sprintList.addAll(page.sprints());

            System.out.println("Nombre de sprints filtrés : " + sprintList.size());

            if (page.isLast()) break;
            startAt += maxResults;
        }

        return sprintList;
    }

    public List<SprintVersionEpicDurationDTO> getEpicDurationsByVersionForBoard(long boardId, String projectKey) throws Exception {
        List<SprintInfoDTO> sprints = getAllSprintsForBoard(boardId).stream()
                .filter(s -> s.getStartDate() != null && s.getEndDate() != null)
                .sorted(Comparator.comparing(SprintInfoDTO::getStartDate))
                .toList();

        List<JsonNode> epicIssues = searchIssuesByJql(
                String.format("project = %s AND issuetype = Epic", projectKey),
                List.of("key", "summary", "status", "created", "resolutiondate", "fixVersions")
        );

        Map<String, Set<Long>> sprintIdsByEpic = new HashMap<>();
        for (JsonNode issue : epicIssues) {
            String epicKey = issue.path("key").asText();
            sprintIdsByEpic.put(epicKey, getSprintIdsForEpicFromChildren(projectKey, epicKey));
        }

        List<SprintVersionEpicDurationDTO> result = new ArrayList<>();
        for (SprintInfoDTO sprint : sprints) {
            Map<String, List<EpicDurationEntryDTO>> epicsByVersion = new HashMap<>();

            for (JsonNode issue : epicIssues) {
                String epicKey = issue.path("key").asText();
                JsonNode fields = issue.path("fields");

                Set<Long> sprintIds = sprintIdsByEpic.getOrDefault(epicKey, Set.of());
                if (!sprintIds.contains(sprint.getId())) {
                    continue;
                }

                String epicSummary = fields.path("summary").asText("Sans résumé");
                String status = fields.path("status").path("name").asText("Inconnu");
                ZonedDateTime createdDate = jiraParser.parseJiraDate(fields.path("created").asText(null), Z_PARIS);
                ZonedDateTime resolutionDate = jiraParser.parseJiraDate(fields.path("resolutiondate").asText(null), Z_PARIS);
                double durationDays = computeDurationWithinSprint(createdDate, resolutionDate, sprint.getStartDate(), sprint.getEndDate());

                JsonNode fixVersions = fields.path("fixVersions");
                if (fixVersions.isArray() && fixVersions.size() > 0) {
                    for (JsonNode versionNode : fixVersions) {
                        String versionName = versionNode.path("name").asText("Sans version");
                        epicsByVersion
                                .computeIfAbsent(versionName, ignored -> new ArrayList<>())
                                .add(new EpicDurationEntryDTO(epicKey, epicSummary, status, durationDays, sprintIds.stream().sorted().toList()));
                    }
                } else {
                    epicsByVersion
                            .computeIfAbsent("Sans version", ignored -> new ArrayList<>())
                            .add(new EpicDurationEntryDTO(epicKey, epicSummary, status, durationDays, sprintIds.stream().sorted().toList()));
                }
            }

            for (Map.Entry<String, List<EpicDurationEntryDTO>> entry : epicsByVersion.entrySet()) {
                List<EpicDurationEntryDTO> epics = entry.getValue();
                double total = BurnupUtils.roundToTwoDecimals(epics.stream().mapToDouble(EpicDurationEntryDTO::durationDays).sum());
                int count = epics.size();
                double average = count == 0 ? 0.0 : BurnupUtils.roundToTwoDecimals(total / count);

                result.add(new SprintVersionEpicDurationDTO(
                        sprint.getId(),
                        sprint.getName(),
                        entry.getKey(),
                        count,
                        total,
                        average,
                        epics
                ));
            }
        }

        result.sort(Comparator
                .comparing(SprintVersionEpicDurationDTO::sprintId)
                .thenComparing(SprintVersionEpicDurationDTO::versionName));
        return result;
    }

    private Set<Long> getSprintIdsForEpicFromChildren(String projectKey, String epicKey) throws Exception {
        String epicLinkJql = String.format("project = %s AND \"Epic Link\" = \"%s\"", projectKey, epicKey);
        List<JsonNode> childIssues;

        try {
            childIssues = searchIssuesByJql(epicLinkJql, List.of("customfield_10020"));
        } catch (Exception ex) {
            String parentJql = String.format("project = %s AND parent = \"%s\"", projectKey, epicKey);
            childIssues = searchIssuesByJql(parentJql, List.of("customfield_10020"));
        }

        Set<Long> sprintIds = new TreeSet<>();
        for (JsonNode childIssue : childIssues) {
            JsonNode sprintField = childIssue.path("fields").path("customfield_10020");
            if (!sprintField.isArray()) {
                continue;
            }
            for (JsonNode sprintNode : sprintField) {
                if (sprintNode.has("id")) {
                    sprintIds.add(sprintNode.path("id").asLong());
                }
            }
        }
        return sprintIds;
    }

    private List<JsonNode> searchIssuesByJql(String jql, List<String> fields) throws Exception {
        final String urlString = JIRA_URL + SEARCH_JQL_API;
        final int maxResults = 50;
        String nextPageToken = null;
        boolean isLast = false;
        List<JsonNode> issues = new ArrayList<>();

        while (!isLast) {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("jql", jql);
            body.put("maxResults", maxResults);
            if (nextPageToken != null) {
                body.put("nextPageToken", nextPageToken);
            }

            ArrayNode fieldsNode = body.putArray("fields");
            for (String field : fields) {
                fieldsNode.add(field);
            }

            HttpRequest request = jiraHttpClient.createPostJsonRequest(urlString, body.toString());
            String response = jiraHttpClient.sendRequest(request);
            JsonNode root = MAPPER.readTree(response);

            for (JsonNode issue : root.path("issues")) {
                issues.add(issue);
            }

            isLast = root.path("isLast").asBoolean(false);
            nextPageToken = root.path("nextPageToken").isMissingNode() ? null : root.path("nextPageToken").asText(null);
        }

        return issues;
    }

    private double computeDurationWithinSprint(ZonedDateTime createdDate,
                                               ZonedDateTime resolutionDate,
                                               ZonedDateTime sprintStart,
                                               ZonedDateTime sprintEnd) {
        if (createdDate == null || sprintStart == null || sprintEnd == null) {
            return 0.0;
        }

        ZonedDateTime effectiveStart = createdDate.isAfter(sprintStart) ? createdDate : sprintStart;
        ZonedDateTime rawEnd = resolutionDate != null ? resolutionDate : sprintEnd;
        ZonedDateTime effectiveEnd = rawEnd.isBefore(sprintEnd) ? rawEnd : sprintEnd;

        if (effectiveEnd.isBefore(effectiveStart)) {
            return 0.0;
        }

        long hours = java.time.Duration.between(effectiveStart, effectiveEnd).toHours();
        return BurnupUtils.roundToTwoDecimals(hours / 24.0);
    }


    // =====================================================================
    // Analyse des tickets du sprint (engagement / ajouts / retraits)
    // =====================================================================

    public record SprintCommitInfo(
            List<Ticket> committedAtStart,
            List<Ticket> addedDuring,
            List<Ticket> removedDuring) {
    }


    public SprintCommitInfo analyseTicketsSprint(String sprintId) throws Exception {
        SprintInfoDTO sprintInfo = getSprintInfo(sprintId);
        Optional<SprintInfo> siOpt = sprintService.getById(Long.valueOf(sprintId));
        ZonedDateTime sprintStart = siOpt.map(SprintInfo::getStartDate)
                .filter(Objects::nonNull)
                .orElseGet(() -> sprintInfo.getStartDate());

        ZonedDateTime sprintEnd = siOpt.map(SprintInfo::getEndDate)
                .filter(Objects::nonNull)
                .orElseGet(() -> sprintInfo.getEndDate());

        Set<Ticket> tickets = collectTicketsForSprintAnalysis(sprintId, sprintStart, sprintEnd);
        System.out.println("collectTicketsForSprintAnalysis terminée");

        JiraSprintAnalysisService.SprintCommitBuckets buckets =
                jiraSprintAnalysisService.classifyTicketsByChangelog(tickets, sprintId, sprintStart, sprintEnd, this::getChangelogCached);

        return new SprintCommitInfo(buckets.committedAtStart(), buckets.addedDuring(), buckets.removedDuring());
    }

    private Set<Ticket> collectTicketsForSprintAnalysis(String sprintId, ZonedDateTime sprintStart, ZonedDateTime sprintEnd) throws Exception {
        List<Ticket> inWindow = getTicketsParJql(getTicketBetweenDateSprint(sprintStart, sprintEnd), sprintStart, false);
        List<Ticket> stillIn = getTicketsParJql(getTicketsSprint(sprintId), sprintStart, true);

        return Stream.concat(inWindow.stream(), stillIn.stream())
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Ticket::getTicketKey))));
    }

    // =====================================================================
    // JQL helpers
    // =====================================================================

    private static String getTicketBetweenDateSprint(ZonedDateTime start, ZonedDateTime end) {
        return new JqlBuilder()
                .project("SAG")
                .and().issuetypeIn(LISTE_TYPES)
                .and().assigneeNotInOrEmpty(LISTE_COMPTES)
                .and().statusIn(List.of("A FAIRE", "ON GOING", "PAIR REVIEW", "À FAIRE"))
                .and().updatedBetween(start, end.plusSeconds(1))
                .build();
    }

    private static String getTicketsSprint(String sprintId) {
        return new JqlBuilder()
                .project("SAG")
                .and().issuetypeIn(LISTE_TYPES)
                .and().statusIn(LISTE_STATUTS_COMPLETS)
                .and().assigneeNotInOrEmpty(LISTE_COMPTES)
                .and().sprintEquals(sprintId)
                .orderBy("status DESC, Rank ASC")
                .build();
    }

    /**
     * Détection robuste d’un id de sprint dans les valeurs 'from'/'to' (simples ou listes).
     */
    private boolean containsSprint(String raw, String sprintId) {
        if (raw == null || sprintId == null) return false;
        String s = raw.trim();
        if (s.equals(sprintId)) return true;
        s = s.replaceAll("[\\[\\]\\s]", "");
        if (s.isEmpty()) return false;
        for (String part : s.split(",")) {
            if (sprintId.equals(part)) return true;
        }
        return false;
    }

    // =====================================================================
    // Constantes métier
    // =====================================================================

    private static final List<String> LISTE_TYPES = List.of(
            "Analyse technique", "Bug", "Story", "Task", "Tâche DevOps", "Tâche Enovacom",
            "Tâche Technique", "feature", "Sub-task", "sub task Enovacom",
            "tâche environnement de travail", "Document", "Affinage fonctionnel");

    private static final List<String> LISTE_TYPES_STORY = List.of(
            "Story");

    private static final List<String> LISTE_STATUTS_COMPLETS = List.of(
            "TACHE TECHNIQUE TESTEE", "A FAIRE", "A VALIDER", "DEV TERMINE", "FAIT",
            "INTEGRATION PR", "LIVRÉ À TESTER", "NON TESTABLE", "ON GOING",
            "PAIR REVIEW", "READY TO DEMO", "RESOLU",
            "RETOUR DEV KO", "RETOUR KO", "TESTÉ", "À FAIRE", "TESTS UTR", "TERMINE");

    private static final Set<String> LISTE_COMPTES = Set.of(
            "70121:346251f4-896a-429b-ac3a-134f9cf8d62d",
            "712020:7e4d7f50-86f4-4786-87b0-a6a71eed41ff",
            "70121:f014ba81-a675-4242-b241-1e412f787109",
            "5fe1c6df91bb2e01084ceec4",
            "712020:c7830591-9fd2-4cac-ad39-2a66d44524ba",
            "712020:5597bbff-8fb3-40d2-9cf4-952fd2eb80dc");
}
