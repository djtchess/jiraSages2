package fr.agile.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.agile.IssueChangelogData;
import fr.agile.JiraApiClient;
import fr.agile.dto.CapaciteDevProchainSprintDTO;
import fr.agile.dto.CapaciteDeveloppeurDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.entities.JoursFeries;
import fr.agile.entities.SprintDevelopperAvailability;
import fr.agile.entities.SprintInfo;
import fr.agile.repository.DevelopperRepository;
import fr.agile.repository.EventRepository;
import fr.agile.repository.JoursFeriesRepository;
import fr.agile.repository.SprintDevelopperAvailabilityRepository;
import fr.agile.repository.SprintInfoRepository;
import fr.agile.sprint.SprintCapacityCalculator;

import fr.agile.model.dto.Ticket;
import fr.agile.utils.JqlBuilder;

// src/main/java/fr/agile/service/SprintCapacityService.java
// ... imports ...
@Service
public class SprintCapacityService {

    // existants
    @Autowired private DevelopperRepository developperRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private JoursFeriesRepository joursFeriesRepository;
    @Autowired private SprintInfoRepository sprintInfoRepository;
    @Autowired private JiraApiClient jiraApiClient;

    @Autowired private SprintDevelopperAvailabilityRepository sdaRepository;

    // nouveau
    @Autowired private SprintDevelopperAvailabilityRepository sprintDevAvailRepository;

    @Transactional(readOnly = true)
    public CapaciteDevProchainSprintDTO getCapaciteEtRestePourDev(Long boardId, Long nextSprintId, Long developperId) throws Exception {

        SprintInfo nextSprint = sprintInfoRepository.findById(nextSprintId).orElse(null);

        // --- 0) Développeur
        Developper dev = developperRepository.findById(developperId)
                .orElseThrow(() -> new IllegalArgumentException("Développeur introuvable: " + developperId));

        // --- 1) Capacité BRUTE (jours ouvrés – fériés – événements)
        var calc         = new SprintCapacityCalculator();
        List<JoursFeries> joursFeries = joursFeriesRepository.findAll();
        List<Event> evenements        = eventRepository.findByDevelopper_IdAndDateDebutEventLessThanEqualAndDateFinEventGreaterThanEqual(developperId, nextSprint.getStartDate().toLocalDate(), nextSprint.getEndDate().toLocalDate());

        var joursTravailles   = calc.calculerJoursTravailles(dev, nextSprint.getStartDate().toLocalDate(), nextSprint.getEndDate().toLocalDate(), joursFeries, evenements);
        double capaciteBrute  = calc.calculerCapacite(joursTravailles, nextSprint.getVelocityStart());

        // --- 2) Facteur de disponibilité (par sprint & dev) — par défaut 100%
        double availabilityFactor = sdaRepository
                .findBySprint_IdAndDevelopper_Id(nextSprintId, developperId)
                .map(SprintDevelopperAvailability::getFactor) // percent / 100
                .orElse(1.0);
        double capaciteNouveau = capaciteBrute * availabilityFactor;

        // --- 3) Reste à faire du sprint précédent
        SprintInfo currentSprint = sprintInfoRepository.findFirstByOriginBoardIdAndStateIgnoreCaseOrderByStartDateDesc(boardId, "active").orElse(null);

        double restePoints = 0.0;
        List<String> ticketsRestantsKeys = Collections.emptyList();

        if (currentSprint != null) {
            // 3.a) Tous les tickets du sprint précédent (engagés + ajoutés)
            String jql = getTicketsSprint(String.valueOf(currentSprint.getId()));
            System.out.println(jql);

            List<Ticket> allPrevTickets = jiraApiClient.getTicketsParJql(jql, currentSprint.getStartDate(), false);

            // 3.b) Filtre "assigné au dev"
            String fullName = (dev.getPrenomDevelopper() + " " + dev.getNomDevelopper()).trim();
            Predicate<Ticket> assignedToDev = t -> {
                String assignee = t.getAssignee();
                return assignee != null && assignee.equalsIgnoreCase(fullName);
            };

            // 3.d) Un ticket est "restant" s’il n’était PAS Done à l’instant du démarrage du nouveau sprint.
            var restants = new ArrayList<Ticket>();
            for (var t : allPrevTickets) {
                if (assignedToDev.test(t) && !SprintStatsService.STATUTS_TERMINES.contains(t.getStatus())) {
                    restants.add(t);
                }
            }

            restePoints = restants.stream()
                    .filter(t -> t.getStoryPoints() != null) // on ignore les null
                    .mapToDouble(t -> {
                        double sp = t.getStoryPoints();
                        double avancement = t.getAvancement() != null ? t.getAvancement() : 0.0; // en %
                        return sp * (1.0 - avancement / 100.0);
                    })
                    .sum();
            ticketsRestantsKeys = restants.stream().map(Ticket::getTicketKey).toList();
        }

        // --- 4) Capacité NETTE
        double capaciteNette = Math.max(0.0, capaciteNouveau - restePoints);

        // --- 5) DTO
        var dto = new CapaciteDevProchainSprintDTO();
        dto.setIdDevelopper(dev.getId());
        dto.setNom(dev.getNomDevelopper());
        dto.setPrenom(dev.getPrenomDevelopper());
        dto.setCapaciteNouveauSprint(round2(capaciteNouveau));
        dto.setResteAFaireSprintPrecedent(round2(restePoints));
        dto.setCapaciteNette(round2(capaciteNette));
        dto.setTicketsRestantsKeys(ticketsRestantsKeys);
        return dto;
    }

    // Helper d’arrondi (à mettre dans la classe)
    private static double round2(double v) {
        return new BigDecimal(v)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Renvoie le pourcentage de disponibilité (0..100), 100 si non défini. */
    @Transactional(readOnly = true)
    public double getAvailabilityPercent(Long sprintId, Long devId) {
        return sdaRepository.findBySprint_IdAndDevelopper_Id(sprintId, devId)
                .map(a -> a.getAvailabilityPercent().doubleValue())
                .orElse(100.0);
    }

    /** Crée ou met à jour la disponibilité d’un dev pour un sprint. */
    @Transactional
    public void upsertAvailability(Long sprintId, Long devId, double percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Le pourcentage doit être entre 0 et 100");
        }

        SprintInfo sprint = sprintInfoRepository.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint introuvable: " + sprintId));
        Developper dev = developperRepository.findById(devId)
                .orElseThrow(() -> new IllegalArgumentException("Développeur introuvable: " + devId));

        SprintDevelopperAvailability entity = sdaRepository
                .findBySprint_IdAndDevelopper_Id(sprintId, devId)
                .orElseGet(() -> {
                    SprintDevelopperAvailability a = new SprintDevelopperAvailability();
                    a.setSprint(sprint);
                    a.setDevelopper(dev);
                    return a;
                });

        entity.setAvailabilityPercent(BigDecimal.valueOf(percent));
        sdaRepository.save(entity);
    }

    /** Liste des disponibilités pour un sprint (id, nom complet, pourcentage). */
    @Transactional(readOnly = true)
    public List<Map<String,Object>> listAvailability(Long sprintId) {
        return sdaRepository.findBySprint_Id(sprintId).stream()
                .map(a -> Map.<String, Object>of(
                        "developerId", a.getDevelopper().getId(),
                        "developer", a.getDevelopper().getPrenomDevelopper() + " " + a.getDevelopper().getNomDevelopper(),
                        "percent", a.getAvailabilityPercent()
                ))
                .toList();
    }


    @Transactional(readOnly = true)
    public List<CapaciteDevProchainSprintDTO> getCapaciteEtRestePourTousDevsOptim(Long boardId, Long nextSprintId) throws Exception {

        SprintInfo nextSprint = sprintInfoRepository.findById(nextSprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint (next) introuvable: " + nextSprintId));

        SprintInfo currentSprint = sprintInfoRepository
                .findFirstByOriginBoardIdAndStateIgnoreCaseOrderByStartDateDesc(boardId, "active")
                .orElse(null);

        // 1) Tickets du sprint courant : 1 seul appel
        List<Ticket> allPrevTickets = Collections.emptyList();
        if (currentSprint != null) {
            String jql = getTicketsSprint(String.valueOf(currentSprint.getId()));
            allPrevTickets = jiraApiClient.getTicketsParJql(jql, currentSprint.getStartDate(), false);
        }

        // 2) Grouper par assigné (lower-case pour comparaison robuste)
        Map<String, List<Ticket>> byAssignee = allPrevTickets.stream()
                .collect(java.util.stream.Collectors.groupingBy(t ->
                        t.getAssignee() == null ? "" : t.getAssignee().toLowerCase()));

        // 3) Préparer constantes communes
        var calc          = new SprintCapacityCalculator();
        List<JoursFeries> joursFeries = joursFeriesRepository.findAll();

        // 4) Pour chaque dev : capacité brute + dispo + reste à faire
        List<Long> ids = List.of(1L, 3L, 4L, 5L, 7L, 8L, 10L);

        List<Developper> devs = developperRepository.findAll().stream()
                .filter(d -> ids.contains(d.getId()))
                .toList();
        List<CapaciteDevProchainSprintDTO> out = new ArrayList<>(devs.size());

        for (Developper dev : devs) {
            String fullName = (dev.getPrenomDevelopper() + " " + dev.getNomDevelopper()).trim();
            List<Ticket> ticketsDev = byAssignee.getOrDefault(fullName.toLowerCase(), Collections.emptyList());

            // événements du dev dans la fenêtre du prochain sprint (LocalDate côté repo)
            List<Event> evenements = eventRepository
                    .findByDevelopper_IdAndDateDebutEventLessThanEqualAndDateFinEventGreaterThanEqual(
                            dev.getId(), nextSprint.getEndDate().toLocalDate(), nextSprint.getStartDate().toLocalDate());

            var joursTravailles = calc.calculerJoursTravailles(
                    dev,
                    nextSprint.getStartDate().toLocalDate(),
                    nextSprint.getEndDate().toLocalDate(),
                    joursFeries,
                    evenements
            );

            double capaciteBrute = calc.calculerCapacite(joursTravailles, nextSprint.getVelocityStart());

            double availabilityFactor = sdaRepository
                    .findBySprint_IdAndDevelopper_Id(nextSprintId, dev.getId())
                    .map(SprintDevelopperAvailability::getFactor)
                    .orElse(1.0);

            double capaciteNouveau = capaciteBrute * availabilityFactor;

            // Tickets restants non “Done” + prise en compte de l'avancement
            Set<String> statutsTermines = SprintStatsService.STATUTS_TERMINES;
            List<Ticket> restants = ticketsDev.stream()
                    .filter(t -> !statutsTermines.contains(t.getStatus()))
                    .toList();

            double restePoints = restants.stream()
                    .filter(t -> t.getStoryPoints() != null)
                    .mapToDouble(t -> {
                        double sp = t.getStoryPoints();
                        double av = t.getAvancement() != null ? t.getAvancement() : 0.0;
                        return sp * (1.0 - av / 100.0);
                    })
                    .sum();

            double capaciteNette = Math.max(0.0, capaciteNouveau - restePoints);

            var dto = new CapaciteDevProchainSprintDTO();
            dto.setIdDevelopper(dev.getId());
            dto.setNom(dev.getNomDevelopper());
            dto.setPrenom(dev.getPrenomDevelopper());
            dto.setCapaciteNouveauSprint(round2(capaciteNouveau));
            dto.setResteAFaireSprintPrecedent(round2(restePoints));
            dto.setCapaciteNette(round2(capaciteNette));
            dto.setTicketsRestantsKeys(restants.stream().map(Ticket::getTicketKey).toList());

            out.add(dto);
        }

        // tri optionnel
        out.sort((a, b) -> Double.compare(b.getCapaciteNette(), a.getCapaciteNette()));
        return out;
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

    private static final List<String> LISTE_TYPES = List.of(
            "Analyse technique", "Bug", "Story", "Task", "Tâche DevOps", "Tâche Enovacom",
            "Tâche Technique", "feature", "Sub-task", "sub task Enovacom",
            "tâche environnement de travail", "Document", "Affinage fonctionnel");

    private static final List<String> LISTE_STATUTS_COMPLETS = List.of(
             "A FAIRE", "ON GOING", "PAIR REVIEW", "RETOUR DEV KO", "RETOUR KO", "À FAIRE");

    private static final Set<String> LISTE_COMPTES = Set.of(
            "70121:346251f4-896a-429b-ac3a-134f9cf8d62d",
            "712020:7e4d7f50-86f4-4786-87b0-a6a71eed41ff",
            "70121:f014ba81-a675-4242-b241-1e412f787109",
            "5fe1c6df91bb2e01084ceec4",
            "712020:c7830591-9fd2-4cac-ad39-2a66d44524ba",
            "712020:5597bbff-8fb3-40d2-9cf4-952fd2eb80dc");
}

