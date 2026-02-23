package fr.agile.service;

import fr.agile.dto.EpicVersionSprintDurationDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.model.dto.Ticket;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EpicVersionStatsServiceTest {

    @Test
    void aggregateEpicVersionDurations_shouldGroupByVersionEpicAndSprint() {
        EpicVersionStatsService service = new EpicVersionStatsService(null);

        SprintInfoDTO sprint = new SprintInfoDTO();
        sprint.setId(101L);
        sprint.setName("Sprint 101");
        sprint.setStartDate(ZonedDateTime.of(2025, 2, 1, 9, 0, 0, 0, ZoneId.of("Europe/Paris")));
        sprint.setEndDate(ZonedDateTime.of(2025, 2, 15, 18, 0, 0, 0, ZoneId.of("Europe/Paris")));

        Ticket t1 = Ticket.builder().versionCorrigee("v1").epicKey("EPIC-1").epicName("Authentification").storyPoints(3d).build();
        Ticket t2 = Ticket.builder().versionCorrigee("v1").epicKey("EPIC-1").epicName("Authentification").storyPoints(5d).build();

        List<EpicVersionSprintDurationDTO> result = service.aggregateEpicVersionDurations(
                List.of(sprint),
                Map.of(101L, List.of(t1, t2))
        );

        assertEquals(1, result.size());
        assertEquals("v1", result.getFirst().getVersion());
        assertEquals("EPIC-1", result.getFirst().getEpicKey());
        assertEquals(8d, result.getFirst().getStoryPoints());
        assertEquals(14d, result.getFirst().getSprintDurationDays());
    }
}
