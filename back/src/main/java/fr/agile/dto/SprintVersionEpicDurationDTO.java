package fr.agile.dto;

import java.util.List;

public record SprintVersionEpicDurationDTO(
        long sprintId,
        String sprintName,
        String versionName,
        int epicCount,
        double totalDurationDays,
        double averageDurationDays,
        List<EpicDurationEntryDTO> epics
) {
}
