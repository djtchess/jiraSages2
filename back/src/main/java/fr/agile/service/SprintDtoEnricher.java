package fr.agile.service;

import java.util.List;

import org.springframework.stereotype.Service;

import fr.agile.dto.SprintInfoDTO;
import fr.agile.utils.BurnupUtils;

@Service
public class SprintDtoEnricher {

    private final SprintService sprintService;

    public SprintDtoEnricher(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    public List<SprintInfoDTO> enrich(List<SprintInfoDTO> sprints) {
        for (SprintInfoDTO dto : sprints) {
            sprintService.getById(dto.getId()).ifPresent(si -> {
                if (si.getVelocity() != null) {
                    dto.setVelocity(BurnupUtils.roundToTwoDecimals(si.getVelocity()));
                }
                if (si.getVelocityStart() != null) {
                    dto.setVelocityStart(BurnupUtils.roundToTwoDecimals(si.getVelocityStart()));
                }
                if (si.getEndDate() != null) {
                    dto.setEndDate(si.getEndDate());
                }
                if (si.getStartDate() != null) {
                    dto.setStartDate(si.getStartDate());
                }
                if (si.getCompleteDate() != null) {
                    dto.setCompleteDate(si.getCompleteDate());
                }
            });

            if (dto.getVelocityStart() == null) {
                sprintService.getAvgVelocityLastClosedAndActive(dto.getOriginBoardId(), 5)
                        .ifPresent(dto::setVelocityStart);
            }
        }

        return sprints;
    }
}
