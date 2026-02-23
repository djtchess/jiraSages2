package fr.agile.dto;

public record EpicDurationEntryDTO(
        String epicKey,
        String epicSummary,
        String status,
        double durationDays
) {
}
