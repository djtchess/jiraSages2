package fr.agile.dto;

import java.util.List;

public record EpicChildTicketDTO(
        String ticketKey,
        String ticketUrl,
        String summary,
        String status,
        double storyPoints,
        double timeSpentDays,
        List<String> developers
) {
}
