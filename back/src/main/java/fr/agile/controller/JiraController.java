package fr.agile.controller;

import fr.agile.JiraApiClient;
import fr.agile.dto.BurnupDataDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.dto.SprintKpiInfo;
import fr.agile.dto.SprintVersionEpicDurationDTO;
import fr.agile.dto.EpicDeliveryOverviewDTO;
import fr.agile.exception.JiraIntegrationException;
import fr.agile.service.SprintService;
import fr.agile.service.SprintStatsService;
import fr.agile.utils.BurnupUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jira")
@CrossOrigin(origins = "*") // Autorise les appels depuis Angular
@Validated
public class JiraController {

    private static final Logger log = LoggerFactory.getLogger(JiraController.class);

    @Autowired private JiraApiClient jiraApiClient;
    @Autowired private SprintService sprintService;
    @Autowired private SprintStatsService sprintStatsService;

    @GetMapping("/burnup/{sprintId}")
    public BurnupDataDTO getBurnupData(@PathVariable @Positive Long sprintId) {
        log.info("Calcul du burnup demandé pour le sprint {}", sprintId);
        try {
            return jiraApiClient.calculateBurnupForSprint(String.valueOf(sprintId));
        } catch (Exception ex) {
            throw new JiraIntegrationException("Impossible de récupérer les données burnup depuis Jira.", ex);
        }
    }


    @GetMapping("/{sprintId}")
    public SprintInfoDTO getSprintInfo(@PathVariable @NotBlank String sprintId) {
        log.info("Récupération des informations du sprint {}", sprintId);
        try {
            return jiraApiClient.getSprintInfo(sprintId);
        } catch (Exception ex) {
            throw new JiraIntegrationException("Impossible de récupérer les informations du sprint depuis Jira.", ex);
        }
    }

    @GetMapping(value ="/projects/{projectKey}/sprints", produces = "application/json")
    public ResponseEntity<List<SprintInfoDTO>> getSprintsForProject(@PathVariable @NotBlank String projectKey) {
        log.info("Récupération des sprints pour le projet {}", projectKey);
        List<SprintInfoDTO> sprints;
        try {
            sprints = jiraApiClient.getAllSprintsForBoard(6);
        } catch (Exception ex) {
            throw new JiraIntegrationException("Impossible de récupérer les sprints depuis Jira.", ex);
        }

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
        return ResponseEntity.ok(sprints);
    }


    @GetMapping("/sprints/{id}/full-info")
    public SprintInfoDTO getFullSprintInfo(@PathVariable @NotBlank String id) {
        log.info("Analyse complète demandée pour le sprint {}", id);
        JiraApiClient.SprintCommitInfo commitInfo;
        try {
            commitInfo = jiraApiClient.analyseTicketsSprint(id);
            log.info("Analyse des tickets terminée pour le sprint {}", id);
            SprintKpiInfo kpiInfo = sprintStatsService.computeKpis(commitInfo);
            log.info("Calcul des KPIs terminé pour le sprint {}", id);

            SprintInfoDTO sprintInfoDTO = jiraApiClient.getSprintInfo(id);
            sprintInfoDTO.setCommitInfo(commitInfo);
            sprintInfoDTO.setSprintKpiInfo(kpiInfo);
            return sprintInfoDTO;
        } catch (Exception ex) {
            throw new JiraIntegrationException("Impossible de calculer les informations complètes du sprint.", ex);
        }
    }


    @GetMapping(value = "/projects/{projectKey}/epics/deliveries", produces = "application/json")
    public ResponseEntity<List<EpicDeliveryOverviewDTO>> getEpicDeliveriesByTeam(@PathVariable @NotBlank String projectKey) {
        log.info("Récupération des épics avec sprints/équipes/version pour le projet {}", projectKey);
        try {
            return ResponseEntity.ok(jiraApiClient.getEpicDeliveriesByTeam(projectKey));
        } catch (Exception ex) {
            throw new JiraIntegrationException("Impossible de récupérer les épics avec équipes/sprints/versions.", ex);
        }
    }

    @GetMapping(value = "/projects/{projectKey}/boards/{boardId}/epics/durations", produces = "application/json")
    public ResponseEntity<List<SprintVersionEpicDurationDTO>> getEpicDurationsBySprintAndVersion(
            @PathVariable @NotBlank String projectKey,
            @PathVariable @Positive Long boardId) {
        log.info("Récupération des durées des épics par sprint/version pour le projet {} et board {}", projectKey, boardId);
        try {
            return ResponseEntity.ok(jiraApiClient.getEpicDurationsByVersionForBoard(boardId, projectKey));
        } catch (Exception ex) {
            throw new JiraIntegrationException("Impossible de récupérer les durées des épics par sprint/version.", ex);
        }
    }
}
