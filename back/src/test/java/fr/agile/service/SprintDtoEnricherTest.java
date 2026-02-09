package fr.agile.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.agile.dto.SprintInfoDTO;
import fr.agile.entities.SprintInfo;

@ExtendWith(MockitoExtension.class)
class SprintDtoEnricherTest {

    @Mock
    private SprintService sprintService;

    @InjectMocks
    private SprintDtoEnricher sprintDtoEnricher;

    @Test
    void enrich_shouldHydrateVelocitiesRoundedAndDatesFromPersistedSprint() {
        SprintInfoDTO dto = new SprintInfoDTO();
        dto.setId(12L);
        dto.setOriginBoardId(6L);

        ZonedDateTime startDate = ZonedDateTime.parse("2024-03-01T10:00:00Z");
        ZonedDateTime endDate = ZonedDateTime.parse("2024-03-15T10:00:00Z");
        ZonedDateTime completeDate = ZonedDateTime.parse("2024-03-14T16:00:00Z");

        SprintInfo persisted = new SprintInfo();
        persisted.setVelocity(12.345);
        persisted.setVelocityStart(8.334);
        persisted.setStartDate(startDate);
        persisted.setEndDate(endDate);
        persisted.setCompleteDate(completeDate);

        when(sprintService.getById(12L)).thenReturn(Optional.of(persisted));

        List<SprintInfoDTO> enriched = sprintDtoEnricher.enrich(List.of(dto));

        assertEquals(12.35, enriched.getFirst().getVelocity());
        assertEquals(8.33, enriched.getFirst().getVelocityStart());
        assertEquals(startDate, enriched.getFirst().getStartDate());
        assertEquals(endDate, enriched.getFirst().getEndDate());
        assertEquals(completeDate, enriched.getFirst().getCompleteDate());
    }

    @Test
    void enrich_shouldSetDefaultVelocityStartFromAverageWhenMissing() {
        SprintInfoDTO dto = new SprintInfoDTO();
        dto.setId(99L);
        dto.setOriginBoardId(42L);

        when(sprintService.getById(99L)).thenReturn(Optional.empty());
        when(sprintService.getAvgVelocityLastClosedAndActive(42L, 5)).thenReturn(Optional.of(19.88));

        List<SprintInfoDTO> enriched = sprintDtoEnricher.enrich(List.of(dto));

        assertNotNull(enriched.getFirst().getVelocityStart());
        assertEquals(19.88, enriched.getFirst().getVelocityStart());
        verify(sprintService).getAvgVelocityLastClosedAndActive(42L, 5);
    }
}
