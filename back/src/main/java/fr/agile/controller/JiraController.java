package fr.agile.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.agile.exception.JiraServiceException;

import fr.agile.JiraApiClient;
import fr.agile.dto.BurnupDataDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.dto.SprintKpiInfo;
import fr.agile.entities.SprintInfo;
import fr.agile.service.SprintService;
import fr.agile.service.SprintStatsService;
import fr.agile.utils.BurnupUtils;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/jira")
@CrossOrigin(origins = "*") // Autorise les appels depuis Angular
@Slf4j
public class JiraController {

    @Autowired private JiraApiClient jiraApiClient;
    @Autowired private SprintService sprintService;
    @Autowired private SprintStatsService sprintStatsService;

    @GetMapping("/burnup/{sprintId}")
    public BurnupDataDTO getBurnupData(@PathVariable String sprintId) throws Exception {
        log.info("Demande de burnup pour le sprint {}", sprintId);

        try {
            Long.parseLong(sprintId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("L'identifiant du sprint est invalide : " + sprintId);
        }

        return jiraApiClient.calculateBurnupForSprint(sprintId);
    }


    @GetMapping("/{sprintId}")
    public SprintInfoDTO getSprintInfo(@PathVariable String sprintId) throws Exception {
        log.info("Récupération des informations du sprint {}", sprintId);
        return jiraApiClient.getSprintInfo(sprintId);
    }

    @GetMapping(value ="/projects/{projectKey}/sprints", produces = "application/json")
    public List<SprintInfoDTO> getSprintsForProject(@PathVariable String projectKey) {
        log.info("Récupération des sprints pour le projet {}", projectKey);
        try {
            // Étape 3 : Obtenir les sprints du board
            List<SprintInfoDTO> sprints = jiraApiClient.getAllSprintsForBoard(6);

            // 4) Hydrate velocity & velocityStart depuis la BDD
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
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des sprints pour le projet {}", projectKey, e);
            throw new JiraServiceException("Impossible de récupérer les sprints pour le projet " + projectKey, e);
        }
    }


    @GetMapping("/sprints/{id}/full-info")
    public SprintInfoDTO getFullSprintInfo(@PathVariable String id) throws Exception {
        log.debug("Analyse complète du sprint {} demandée", id);
        JiraApiClient.SprintCommitInfo commitInfo = jiraApiClient.analyseTicketsSprint(id);
        log.debug("analyseTicketsSprint terminée pour le sprint {}", id);
        SprintKpiInfo kpiInfo = sprintStatsService.computeKpis(commitInfo);
        log.debug("computeKpis terminée pour le sprint {}", id);

        SprintInfoDTO sprintInfoDTO = jiraApiClient.getSprintInfo(id);
        sprintInfoDTO.setCommitInfo(commitInfo);
        sprintInfoDTO.setSprintKpiInfo(kpiInfo);

        return sprintInfoDTO;
    }


}
