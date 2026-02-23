package fr.agile.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.agile.JiraApiClient;
import fr.agile.dto.BurnupDataDTO;
import fr.agile.dto.EpicVersionSprintDurationDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.dto.SprintKpiInfo;
import fr.agile.service.EpicVersionStatsService;
import fr.agile.service.SprintService;
import fr.agile.service.SprintStatsService;
import fr.agile.utils.BurnupUtils;

@RestController
@RequestMapping("/api/jira")
@CrossOrigin(origins = "*") // Autorise les appels depuis Angular
public class JiraController {

    private static final Logger log = LoggerFactory.getLogger(JiraController.class);

    @Autowired private JiraApiClient jiraApiClient;
    @Autowired private SprintService sprintService;
    @Autowired private SprintStatsService sprintStatsService;
    @Autowired private EpicVersionStatsService epicVersionStatsService;

    @GetMapping("/burnup/{sprintId}")
    public BurnupDataDTO getBurnupData(@PathVariable Long sprintId) throws Exception {
        log.info("Calcul du burnup demandé pour le sprint {}", sprintId);
        return jiraApiClient.calculateBurnupForSprint(String.valueOf(sprintId));
    }


    @GetMapping("/{sprintId}")
    public SprintInfoDTO getSprintInfo(@PathVariable String sprintId) throws Exception {
        log.info("Récupération des informations du sprint {}", sprintId);
        return jiraApiClient.getSprintInfo(sprintId);
    }

    @GetMapping(value ="/projects/{projectKey}/sprints", produces = "application/json")
    public ResponseEntity<List<SprintInfoDTO>> getSprintsForProject(@PathVariable String projectKey) {
        log.info("Récupération des sprints pour le projet {}", projectKey);
        List<SprintInfoDTO> sprints = jiraApiClient.getAllSprintsForBoard(6);

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


    @GetMapping(value ="/projects/{projectKey}/epics-by-version", produces = "application/json")
    public ResponseEntity<List<EpicVersionSprintDurationDTO>> getEpicsByVersion(@PathVariable String projectKey) throws Exception {
        log.info("Récupération des epics par version pour le projet {}", projectKey);
        return ResponseEntity.ok(epicVersionStatsService.getEpicDurationsByVersion(projectKey));
    }


    @GetMapping("/sprints/{id}/full-info")
    public SprintInfoDTO getFullSprintInfo(@PathVariable String id) throws Exception {
        log.info("Analyse complète demandée pour le sprint {}", id);
        JiraApiClient.SprintCommitInfo commitInfo = jiraApiClient.analyseTicketsSprint(id);
        log.info("Analyse des tickets terminée pour le sprint {}", id);
        SprintKpiInfo kpiInfo = sprintStatsService.computeKpis(commitInfo);
        log.info("Calcul des KPIs terminé pour le sprint {}", id);

        SprintInfoDTO sprintInfoDTO = jiraApiClient.getSprintInfo(id);
        sprintInfoDTO.setCommitInfo(commitInfo);
        sprintInfoDTO.setSprintKpiInfo(kpiInfo);

        return sprintInfoDTO;
    }


}
