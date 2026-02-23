package fr.agile.dto;

import java.util.List;

public record EpicDeliveryOverviewDTO(
        String epicKey,
        String epicSummary,
        String status,
        List<String> versionNames,
        List<EpicTeamSprintDTO> sprintDeliveries
) {
}
