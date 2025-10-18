package fr.agile.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import fr.agile.JiraApiClient;
import fr.agile.dto.CountersInfo;
import fr.agile.dto.SprintKpiInfo;
import fr.agile.model.dto.Ticket;

@Service
public class SprintStatsService {

    public static final Set<String> STATUTS_TERMINES = Set.of(
            "TACHE TECHNIQUE TESTEE",
            "DEV TERMINE",
            "FAIT",
            "LIVRÉ À TESTER",
            "NON TESTABLE",
            "RESOLU",
            "TESTÉ",
            "TESTS UTR",
            "TERMINE",
            "READY TO DEMO",
            "INTEGRATION PR"
    );

    public SprintKpiInfo computeKpis(JiraApiClient.SprintCommitInfo commitInfo) {
        List<Ticket> committed = commitInfo.committedAtStart();
        List<Ticket> added = commitInfo.addedDuring();
        List<Ticket> removed = commitInfo.removedDuring();

        int committedTotal = committed.size();
        int addedTotal = added.size();
        int removedTotal = removed.size();

        Predicate<Ticket> isDone = t -> t.getStatus() != null && STATUTS_TERMINES.contains(t.getStatus().toUpperCase());

        int committedAndDone = (int) committed.stream().filter(isDone).count();
        int doneAll = (int) Stream.concat(committed.stream(), added.stream()).filter(isDone).count();

        int devBefore = (int) committed.stream().filter(Ticket::isDevTermineAvantSprint).count();

        int nonTerminesEngages = committedTotal - committedAndDone;

        int totalReal = committedTotal + addedTotal;

        double pointsCommited = committed.stream()
                .mapToDouble(Ticket::remainingSp)
                .sum();

        double pointsAdded = added.stream()
                .mapToDouble(Ticket::remainingSp)
                .sum();

        double pointsRemoved = removed.stream()
                .mapToDouble(Ticket::remainingSp)
                .sum();

        // === Nouveaux compteurs par type ===
        Map<String, CountersInfo> typeInfoCommitted = committed.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getType() != null ? t.getType() : "UNKNOWN",
                        Collectors.collectingAndThen(
                                Collectors.summarizingDouble(Ticket::getStoryPoints),
                                stats -> new CountersInfo((int) stats.getCount(),
                                        (int) stats.getSum())
                        )));

        Map<String, CountersInfo> typeInfoAdded = added.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getType() != null ? t.getType() : "UNKNOWN",
                        Collectors.collectingAndThen(
                                Collectors.summarizingDouble(Ticket::getStoryPoints),
                                stats -> new CountersInfo((int) stats.getCount(),
                                        (int) stats.getSum())
                        )));

        Map<String, CountersInfo> typeInfoAll = Stream.concat(committed.stream(),
                        added.stream())
                .collect(Collectors.groupingBy(
                        t -> t.getType() != null ? t.getType() : "UNKNOWN",
                        Collectors.collectingAndThen(
                                Collectors.summarizingDouble(Ticket::getStoryPoints),
                                stats -> new CountersInfo((int) stats.getCount(),
                                        (int) stats.getSum())
                        )));

        return new SprintKpiInfo(
                totalReal,
                committedTotal,
                committedAndDone,
                addedTotal,
                removedTotal,
                doneAll,
                devBefore,
                committedTotal == 0 ? 0 : (100.0 * committedAndDone) / committedTotal,
                totalReal == 0 ? 0 : (100.0 * addedTotal) / totalReal,
                committedTotal == 0 ? 0 : (100.0 * (nonTerminesEngages)) / committedTotal,
                totalReal == 0 ? 0 : (100.0 * doneAll) / totalReal,
                pointsCommited,
                pointsAdded,
                pointsRemoved,
                typeInfoCommitted,
                typeInfoAdded,
                typeInfoAll
        );
    }


}
