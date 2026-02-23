package fr.agile;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import java.util.concurrent.ConcurrentHashMap;
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
import fr.agile.dto.SprintInfoDTO;
import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.entities.JoursFeries;
import fr.agile.entities.SprintInfo;
import fr.agile.model.dto.Ticket;
import fr.agile.service.DevelopperService;
import fr.agile.service.EventService;
import fr.agile.service.JiraHttpClient;
import fr.agile.service.JoursFeriesService;
import fr.agile.service.SprintService;
import fr.agile.sprint.SprintCapacityCalculator;
import fr.agile.utils.BurnupUtils;
import fr.agile.utils.JqlBuilder;

@Component
public class JiraApiClient {

    @Value("${jira.baseUrl}")
    private String JIRA_URL;

    @Value("${jira.username}")
    private String USERNAME;

    @Value("${jira.apiToken}")
    private String API_TOKEN;

    private static final String SEARCH_API = "/rest/api/3/search";
    private static final String ISSUE_API = "/rest/api/3/issue/";
    private static final String SEARCH_JQL_API = "/rest/api/3/search/jql";

    private static final ZoneId Z_PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter JIRA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    private static final ObjectMapper mapper = new ObjectMapper();

    // (Optionnel) Conserve l'ancienne constante si utilisée ailleurs
    private static final String FIELDS = String.join(",",
            "key", "assignee", "status", "created", "fixVersions",
            "customfield_10028",     // story points
            "customfield_10126",     // avancement
            "customfield_10020"      // sprint(s)
    );

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
    private final Map<String, IssueChangelogData> changelogCache = new ConcurrentHashMap<>();

    public JiraApiClient(SprintService sprintService,
                         DevelopperService developpeurService,
                         EventService evenementService,
                         JoursFeriesService joursFeriesService,
                         SprintCapacityCalculator calculator,
                         JiraHttpClient jiraHttpClient) {
        this.developpeurService = developpeurService;
        this.evenementService = evenementService;
        this.joursFeriesService = joursFeriesService;
        this.calculator = calculator;
        this.sprintService = sprintService;
        this.jiraHttpClient = jiraHttpClient;
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
            // Corps JSON VALIDE: pas de startAt sur /search/jql
            ObjectNode body = mapper.createObjectNode();
            body.put("jql", jql);
            body.put("maxResults", maxResults);
            if (nextPageToken != null) {
                body.put("nextPageToken", nextPageToken);
            }

            // fields doit être un tableau JSON
            ArrayNode fieldsArray = body.putArray("fields");
            fieldsArray.add("key")
                    .add("assignee")
                    .add("status")
                    .add("created")
                    .add("fixVersions")
                    .add("issuetype")
                    .add("customfield_10028")   // story points
                    .add("customfield_10126")   // avancement
                    .add("customfield_10020")   // sprint(s)
                    .add("customfield_10242");  // engagement sprint ? (si présent)

            HttpRequest request = jiraHttpClient.createPostJsonRequest(urlString, body.toString());
            String response = jiraHttpClient.sendRequest(request);

            JsonNode rootNode = mapper.readTree(response);
            JsonNode issuesNode = rootNode.path("issues");

            for (JsonNode issue : issuesNode) {
                String key = issue.path("key").asText();
                JsonNode fields = issue.path("fields");

                String assignee = fields.path("assignee").isMissingNode() || fields.path("assignee").isNull()
                        ? "Non assigné"
                        : fields.path("assignee").path("displayName").asText();

                String status = fields.path("status").path("name").asText("Inconnu");
                String storyPts = fields.path("customfield_10028").isNull() ? null : fields.path("customfield_10028").asText();
                String avancement = fields.path("customfield_10126").isNull() ? null : fields.path("customfield_10126").asText();
                String type = fields.path("issuetype").path("name").asText("Inconnu");
                String url = JIRA_URL + "/browse/" + key;

                JsonNode customField = fields.path("customfield_10242");
                String engagementSprint = null;
                if (customField.isArray() && customField.size() > 0) {
                    engagementSprint = customField.get(0).path("value").asText(null);
                }

                JsonNode fixVersionsNode = fields.path("fixVersions");
                String versionCorrigee = null;
                if (fixVersionsNode.isArray() && fixVersionsNode.size() > 0) {
                    versionCorrigee = fixVersionsNode.get(0).path("name").asText();
                }

                LocalDate created = LocalDate.parse(fields.path("created").asText().substring(0, 10));

                // SprintIds liés
                List<String> sprintIds = new ArrayList<>();
                JsonNode sprintField = fields.path("customfield_10020");
                if (sprintField.isArray()) {
                    for (JsonNode s : sprintField) {
                        if (s.has("id")) sprintIds.add(s.path("id").asText());
                    }
                }

                // Filtrage "Dev terminé avant le sprint"
                boolean devAvantSprint = false;
                if (ticketDevAvantSprint) {
                    try {
                        IssueChangelogData ch = getChangelogCached(key);
                        devAvantSprint = isDevTermineAvantSprintFromData(ch, sprintStartDate);
                    } catch (Exception e) {
                        devAvantSprint = false; // ne filtre pas si erreur changelog
                    }
                }

                Ticket t = Ticket.builder()
                        .ticketKey(key)
                        .url(url)
                        .assignee(assignee)
                        .status(status)
                        .storyPoints(BurnupUtils.parseDouble(storyPts))
                        .storyPointsRealiseAvantSprint(null)
                        .avancement(BurnupUtils.parseDouble(avancement))
                        .engagementSprint(engagementSprint)
                        .type(type)
                        .versionCorrigee(versionCorrigee)
                        .createdDate(created)
                        .sprintIds(sprintIds)
                        .build();

                if (devAvantSprint) {
                    t.setDevTermineAvantSprint(true);
                } else {
                    ticketList.add(t);
                }
            }

            // Nouvelle pagination: isLast + nextPageToken
            isLast = rootNode.path("isLast").asBoolean(false);
            nextPageToken = rootNode.path("nextPageToken").isMissingNode()
                    ? null
                    : rootNode.path("nextPageToken").asText(null);
        }

        return ticketList;
    }

    // =====================================================================
    // Changelog (avec cache + vraie pagination)
    // =====================================================================

    // Cache changelog avec TTL
    private final Map<String, Instant> changelogCacheTs = new ConcurrentHashMap<>();
    private static final Duration CHANGELOG_TTL = Duration.ofHours(2);

    private IssueChangelogData getChangelogCached(String issueKey) throws Exception {
        var now = Instant.now();
        var ts = changelogCacheTs.get(issueKey);
        var cached = changelogCache.get(issueKey);
        if (cached != null && ts != null && ts.plus(CHANGELOG_TTL).isAfter(now)) {
            return cached;
        }
        var fresh = getChangelogData(issueKey);
        changelogCache.put(issueKey, fresh);
        changelogCacheTs.put(issueKey, now);
        return fresh;
    }

    public void invalidateChangelog(String issueKey) {
        changelogCache.remove(issueKey);
        changelogCacheTs.remove(issueKey);
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

            JsonNode root = mapper.readTree(body);
            // L’endpoint /changelog retourne "values"
            JsonNode histories = root.path("values");
            int total = root.path("total").asInt();

            for (JsonNode history : histories) {
                String createdStr = history.path("created").asText();
                ZonedDateTime createdZdt = ZonedDateTime.parse(createdStr, JIRA_DATE_FORMATTER);

                for (JsonNode item : history.path("items")) {
                    String field = item.path("field").asText("");
                    switch (field.toLowerCase()) {
                        case "status" -> {
                            String toStatus = item.path("toString").asText();
                            statusParDate.put(createdZdt.toLocalDateTime(), toStatus);
                        }
                        case "avancement" -> {
                            String from = getTextSafe(item, "fromString");
                            String to = getTextSafe(item, "toString");
                            avancementMap.put(createdStr, new AvancementHistorique(createdStr, "avancement", from, to));
                        }
                        case "sprint" -> {
                            String from = item.path("from").asText(null);
                            String to = item.path("to").asText(null);
                            sprintHistories.add(new AvancementHistorique(createdStr, "Sprint", from, to));
                        }
                        default -> { /* ignore */ }
                    }
                }
            }

            startAt += maxResults;
            hasMore = startAt < total;
        }

        List<AvancementHistorique> avancementHistorique = new ArrayList<>(avancementMap.values());
        avancementHistorique.sort(Comparator.comparing(a -> ZonedDateTime.parse(a.getDate(), JIRA_DATE_FORMATTER)));

        return new IssueChangelogData(avancementHistorique, statusParDate, sprintHistories);
    }

    private String getTextSafe(JsonNode node, String path) {
        JsonNode child = node.path(path);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    // =====================================================================
    // Aide "dev terminé avant le sprint"
    // =====================================================================

    /**
     * Version basée sur IssueChangelogData (évite de reparser des JsonNode bruts).
     */
    private boolean isDevTermineAvantSprintFromData(IssueChangelogData cd, ZonedDateTime sprintStart) {
        if (cd == null) return false;
        Set<String> validStatuses = Set.of("DEV TERMINE", "INTEGRATION PR", "PAIR REVIEW", "READY TO DEMO");
        // on exploite statusParDate (clé = LocalDateTime)
        for (Map.Entry<LocalDateTime, String> e : cd.getStatutAvantDateMap().entrySet()) {
            ZonedDateTime when = e.getKey().atZone(Z_PARIS);
            String to = e.getValue() == null ? "" : e.getValue().toUpperCase();
            if (validStatuses.contains(to) && when.isBefore(sprintStart)) return true;
        }
        return false;
    }

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
            Double val = calculateAvancementAvantSprint(changelogData, ticket.getStoryPoints(), dateDebut);
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

    // Calcul de l'avancement avant le sprint
    private double calculateAvancementAvantSprint(IssueChangelogData changelogData, double sp, ZonedDateTime dateDebut) {
        double lastAvancementAvantSprint = 0.0;
        String statutAvant = changelogData.getLastStatusBefore(dateDebut);
        Set<String> statutsValides = Set.of("ON GOING", "PAIR REVIEW", "READY TO DEMO", "INTEGRATION PR", "DEV TERMINE");

        if (statutAvant != null && statutsValides.contains(statutAvant.toUpperCase())) {
            for (AvancementHistorique a : changelogData.getAvancementHistorique()) {
                ZonedDateTime date = BurnupUtils.parseZonedDateTime(a.getDate());

                if (date.isBefore(dateDebut) && a.getTo() != null && !a.getTo().isBlank()) {
                    try {
                        double avancement = Double.parseDouble(a.getTo());
                        if (avancement > lastAvancementAvantSprint) {
                            lastAvancementAvantSprint = avancement;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            lastAvancementAvantSprint = BurnupUtils.roundToTwoDecimals(sp * (lastAvancementAvantSprint / 100.0));
        }
        return lastAvancementAvantSprint;
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
        JsonNode sprintNode = mapper.readTree(response);

        String state = sprintNode.path("state").asText();
        String startDateStr = sprintNode.path("startDate").asText(null);
        String endDateStr = sprintNode.path("endDate").asText(null);
        String completeDateStr = sprintNode.path("completeDate").asText(null);

        SprintInfoDTO sprint = new SprintInfoDTO();
        sprint.setId(sprintNode.path("id").asLong());
        sprint.setName(sprintNode.path("name").asText());
        sprint.setState(state);
        sprint.setOriginBoardId(sprintNode.path("originBoardId").asLong());

        // Conversion des String -> ZonedDateTime dans le fuseau que tu utilises (ex: Europe/Paris)
        ZoneId zone = Z_PARIS; // ou ZoneId.of("Europe/Paris")
        sprint.setStartDate(parseJiraDate(startDateStr, zone));
        sprint.setCompleteDate(parseJiraDate(completeDateStr, zone));
        sprint.setEndDate(resolveEffectiveEndDate(state, endDateStr, completeDateStr, zone));

        return sprint;
    }

    // Utils dates (dans JiraApiClient par ex.)
    private static final DateTimeFormatter[] JIRA_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,                         // 2024-08-30T13:07:52Z ou +00:00
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),      // 2024-08-30T13:07:52.000Z
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")           // 2024-08-30T13:07:52Z
    };

    private static ZonedDateTime parseJiraDate(String dateStr, ZoneId targetZone) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (DateTimeFormatter f : JIRA_FORMATS) {
            try {
                // OffsetDateTime -> on respecte l’offset fourni par JIRA puis on convertit dans le fuseau cible
                var odt = java.time.OffsetDateTime.parse(dateStr, f);
                return odt.atZoneSameInstant(targetZone);
            } catch (Exception ignore) { /* on tente le format suivant */ }
        }
        // Dernier filet : si ça finit par 'Z', Instant.parse sait faire.
        try {
            return java.time.Instant.parse(dateStr).atZone(targetZone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Format de date JIRA inattendu: " + dateStr, e);
        }
    }

    /**
     * Calcule la "date de fin effective" en ZonedDateTime.
     * - closed  -> completeDate si présente
     * - active  -> si endDate < aujourd’hui (dans zoneId), clamp à aujourd’hui ; sinon endDate
     * - autres  -> endDate
     */
    public ZonedDateTime resolveEffectiveEndDate(
            String state,
            String endDateStr,
            String completeDateStr,
            ZoneId zoneId
    ) {
        ZonedDateTime endDate = parseJiraDate(endDateStr, zoneId);
        ZonedDateTime completeDate = parseJiraDate(completeDateStr, zoneId);

        if ("closed".equalsIgnoreCase(state) && completeDate != null) {
            return completeDate;
        } else if ("active".equalsIgnoreCase(state) && endDate != null) {
            ZonedDateTime today = ZonedDateTime.now(zoneId);
            if (endDate.toLocalDate().isBefore(today.toLocalDate())) {
                return today;
            }
        }
        return endDate;
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

            JsonNode rootNode = mapper.readTree(response);
            JsonNode values = rootNode.path("values");
            isLast = rootNode.path("isLast").asBoolean();

            for (JsonNode boardNode : values) {
                BoardInfo board = new BoardInfo();
                board.setId(boardNode.path("id").asLong());
                board.setName(boardNode.path("name").asText());
                board.setType(boardNode.path("type").asText());
                boards.add(board);
            }
            startAt += maxResults;
        }
        return boards;
    }

    public List<SprintInfoDTO> getAllSprintsForBoard(long boardId) throws Exception {
        List<SprintInfoDTO> sprintList = new ArrayList<>();
        int startAt = 0;
        int maxResults = 50;

        final ZoneId zone = Z_PARIS; // ou ZoneId.of("Europe/Paris")
        final ZonedDateTime filtreDate = ZonedDateTime.of(2024, 10, 7, 0, 0, 0, 0, zone);

        while (true) {
            String urlString = JIRA_URL + "/rest/agile/1.0/board/" + boardId
                    + "/sprint?startAt=" + startAt + "&maxResults=" + maxResults;
            HttpRequest request = jiraHttpClient.createRequest(urlString);
            String response = jiraHttpClient.sendRequest(request);

            JsonNode root = mapper.readTree(response);
            JsonNode values = root.path("values");

            // Jira renvoie normalement "isLast": true/false
            boolean isLast = root.path("isLast").asBoolean(values.size() < maxResults);

            for (JsonNode sprintNode : values) {
                String state = sprintNode.path("state").asText();
                String startDateStr = sprintNode.path("startDate").asText(null);
                String endDateStr = sprintNode.path("endDate").asText(null);
                String completeDateStr = sprintNode.path("completeDate").asText(null);

                ZonedDateTime startDate = parseJiraDate(startDateStr, zone);
                ZonedDateTime completeDate = parseJiraDate(completeDateStr, zone);
                ZonedDateTime effectiveEnd = resolveEffectiveEndDate(
                        state, endDateStr, completeDateStr, zone);

                SprintInfoDTO sprint = new SprintInfoDTO();
                sprint.setId(sprintNode.path("id").asLong());
                sprint.setName(sprintNode.path("name").asText());
                sprint.setState(state);
                sprint.setStartDate(startDate);
                sprint.setCompleteDate(completeDate);
                sprint.setEndDate(effectiveEnd);
                sprint.setOriginBoardId(boardId);

                if (startDate != null) {
                    if (startDate.isAfter(filtreDate)) {
                        sprintList.add(sprint);
                    }
                } else {
                    System.out.printf("Sprint name=%s n'a pas de date de début%n", sprint.getName());
                }
            }

            System.out.println("Nombre de sprints filtrés : " + sprintList.size());

            if (isLast) break;            // fin de pagination
            startAt += maxResults;        // page suivante
        }

        return sprintList;
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

        return classifyTicketsByChangelog(tickets, sprintId, sprintStart, sprintEnd);
    }

    private Set<Ticket> collectTicketsForSprintAnalysis(String sprintId, ZonedDateTime sprintStart, ZonedDateTime sprintEnd) throws Exception {
        List<Ticket> inWindow = getTicketsParJql(getTicketBetweenDateSprint(sprintStart, sprintEnd), sprintStart, false);
        List<Ticket> stillIn = getTicketsParJql(getTicketsSprint(sprintId), sprintStart, true);

        return Stream.concat(inWindow.stream(), stillIn.stream())
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Ticket::getTicketKey))));
    }

    private SprintCommitInfo classifyTicketsByChangelog(
            Set<Ticket> tickets,
            String sprintId,
            ZonedDateTime sprintStart,
            ZonedDateTime sprintEnd) throws Exception {

        List<Ticket> committed = new ArrayList<>();
        List<Ticket> added = new ArrayList<>();
        List<Ticket> removed = new ArrayList<>();

        int idx = 1;

        for (Ticket ticket : tickets) {
            IssueChangelogData cd = getChangelogCached(ticket.getTicketKey());
            ticket.setChangelog(cd);
            if (ticket.getStoryPoints() == null) {
                ticket.setStoryPoints(0.0);
            }
            ticket.setStoryPointsRealiseAvantSprint(
                    calculateAvancementAvantSprint(cd, ticket.getStoryPoints(), sprintStart));
            ticket.setStatus(cd.getLastStatusBefore(sprintEnd));

            class Change {
                ZonedDateTime when;
                boolean fromContains;
                boolean toContains;

                Change(ZonedDateTime w, boolean f, boolean t) {
                    when = w;
                    fromContains = f;
                    toContains = t;
                }
            }
            List<Change> sprintChanges = new ArrayList<>();

            for (AvancementHistorique h : cd.getSprintHistorique()) {
                if (!"Sprint".equalsIgnoreCase(h.getField())) continue;

                boolean fromContains = containsSprint(h.getFrom(), sprintId);
                boolean toContains = containsSprint(h.getTo(), sprintId);
                if (!fromContains && !toContains) continue;
                if (fromContains == toContains) continue;

                ZonedDateTime when = ZonedDateTime
                        .parse(h.getDate(), JIRA_DATE_FORMATTER)
                        .withZoneSameInstant(sprintStart.getZone());

                sprintChanges.add(new Change(when, fromContains, toContains));
            }

            if (sprintChanges.isEmpty()) {
                fallbackAddOrCommit(ticket, sprintId, sprintStart, committed, added);
                continue;
            }

            sprintChanges.sort(Comparator.comparing(chg -> chg.when));

            boolean inSprintAtStart = false;
            ZonedDateTime firstAddAfterStart = null;
            ZonedDateTime firstRemoveDuring = null;

            for (Change chg : sprintChanges) {
                if (chg.when.isAfter(sprintEnd)) break;

                if (!chg.when.isAfter(sprintStart)) {
                    inSprintAtStart = chg.toContains;
                } else {
                    if (!chg.fromContains && chg.toContains && firstAddAfterStart == null) {
                        firstAddAfterStart = chg.when;
                    }
                    if (chg.fromContains && !chg.toContains && firstRemoveDuring == null) {
                        firstRemoveDuring = chg.when;
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

        return new SprintCommitInfo(committed, added, removed);
    }

    private void fallbackAddOrCommit(Ticket ticket, String sprintId, ZonedDateTime sprintStart,
                                     List<Ticket> committed, List<Ticket> added) {
        if (ticket.getSprintIds().contains(sprintId)) {
            ZonedDateTime created = ticket.getCreatedDate().atStartOfDay(sprintStart.getZone());
            if (created.isAfter(sprintStart)) added.add(ticket);
            else committed.add(ticket);
        }
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
