package fr.agile.dto;

import java.util.List;

public record EpicDurationEntryDTO(
        String epicKey,
        String epicSummary,
        String status,
        double durationDays,
        List<Long> sprintIds
) {
}
