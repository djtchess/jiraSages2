package fr.agile.dto;

import java.util.List;

public record EpicChildTicketDTO(
        String ticketKey,
        String ticketUrl,
        String summary,
        double storyPoints,
        double timeSpentDays,
        List<String> developers
) {
}
