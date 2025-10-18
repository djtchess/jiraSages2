package fr.agile.dto;

import java.util.Map;

public record SprintKpiInfo(
        int totalTickets,
        int committedAtStart,
        int committedAndDone,
        int addedDuring,
        int removedDuring,
        int doneTickets,
        int devDoneBeforeSprint,
        double engagementRespectePourcent,
        double ajoutsNonPrevusPourcent,
        double nonTerminesEngagesPourcent,
        double succesGlobalPourcent,
        double pointsCommited,
        double pointsAdded,
        double pointsRemoved,
        Map<String, CountersInfo> typeCountCommitted,
        Map<String, CountersInfo> typeCountAdded,
        Map<String, CountersInfo> typeCountAll

) {}
