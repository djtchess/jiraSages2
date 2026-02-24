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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

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
import fr.agile.service.ChangelogCacheService;
import fr.agile.service.JiraHttpClient;
import fr.agile.service.JiraParser;
import fr.agile.service.JoursFeriesService;
import fr.agile.service.JiraSprintAnalysisService;
import fr.agile.service.SprintService;
import fr.agile.sprint.SprintCapacityCalculator;
import fr.agile.utils.BurnupUtils;
import fr.agile.utils.JqlBuilder;

@Component
public class JiraApiClient {

    @Value("${jira.baseUrl}")
    private String JIRA_URL;

    @Value("${jira.changelog.parallelism:4}")
    private int changelogParallelism;

    @Value("${jira.changelog.maxQps:8}")
    private int changelogMaxQps;

    private static final String SEARCH_JQL_API = "/rest/api/3/search/jql";

    private static final ZoneId Z_PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter JIRA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

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
    private final Object changelogRateLimitLock = new Object();
    private long nextChangelogSlotNanos = 0L;

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

            if (ticketDevAvantSprint) {
                ticketList.addAll(filterTicketsWithChangelog(ticketPage.tickets(), sprintStartDate));
            } else {
                ticketList.addAll(ticketPage.tickets());
            }

            isLast = ticketPage.isLast();
            nextPageToken = ticketPage.nextPageToken();
        }

        return ticketList;
    }

    private List<Ticket> filterTicketsWithChangelog(List<Ticket> tickets, ZonedDateTime sprintStartDate) {
        if (tickets.isEmpty()) {
            return Collections.emptyList();
        }

        int poolSize = Math.max(1, Math.min(changelogParallelism, tickets.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<TicketFilterResult>> futures = tickets.stream()
                    .map(ticket -> CompletableFuture.supplyAsync(() -> evaluateTicket(ticket, sprintStartDate), executor))
                    .toList();

            List<Ticket> filtered = new ArrayList<>(tickets.size());
            for (CompletableFuture<TicketFilterResult> future : futures) {
                TicketFilterResult result;
                try {
                    result = future.join();
                } catch (CompletionException e) {
                    continue;
                }
                if (result.devTermineAvantSprint()) {
                    result.ticket().setDevTermineAvantSprint(true);
                } else {
                    filtered.add(result.ticket());
                }
            }
            return filtered;
        } finally {
            executor.shutdown();
        }
    }

    private TicketFilterResult evaluateTicket(Ticket ticket, ZonedDateTime sprintStartDate) {
        try {
            IssueChangelogData changelog = getChangelogCached(ticket.getTicketKey());
            boolean devAvantSprint = jiraSprintAnalysisService.isDevTermineAvantSprint(changelog, sprintStartDate);
            return new TicketFilterResult(ticket, devAvantSprint);
        } catch (Exception e) {
            return new TicketFilterResult(ticket, false);
        }
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
            throttleChangelogRequests();
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

    private void throttleChangelogRequests() throws InterruptedException {
        if (changelogMaxQps <= 0) {
            return;
        }
        long intervalNanos = TimeUnit.SECONDS.toNanos(1) / changelogMaxQps;
        synchronized (changelogRateLimitLock) {
            long now = System.nanoTime();
            if (nextChangelogSlotNanos > now) {
                TimeUnit.NANOSECONDS.sleep(nextChangelogSlotNanos - now);
                now = System.nanoTime();
            }
            nextChangelogSlotNanos = Math.max(nextChangelogSlotNanos, now) + intervalNanos;
        }
    }

    private record TicketFilterResult(Ticket ticket, boolean devTermineAvantSprint) {
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
