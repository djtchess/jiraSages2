package fr.agile.dto;

import java.util.List;

public record EpicChildTicketDTO(
        String ticketKey,
        String ticketUrl,
        String summary,
        double timeSpentHours,
        List<String> developers
) {
}
