package fr.agile.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.agile.JiraApiClient;
import fr.agile.dto.BoardInfo;
import fr.agile.dto.BurnupDataDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.dto.SprintKpiInfo;
import fr.agile.entities.SprintInfo;
import fr.agile.service.SprintService;
import fr.agile.service.SprintStatsService;
import fr.agile.utils.BurnupUtils;

@RestController
@RequestMapping("/api/jira")
@CrossOrigin(origins = "*") // Autorise les appels depuis Angular
public class JiraController {

    @Autowired private JiraApiClient jiraApiClient;
    @Autowired private SprintService sprintService;
    @Autowired private SprintStatsService sprintStatsService;

@GetMapping("/burnup/{sprintId}")
public BurnupDataDTO getBurnupData(@PathVariable String sprintId) throws Exception {
    System.out.println("Controller getBurnupData appelé");

    Long sprintIdLong;
    try {
        sprintIdLong = Long.parseLong(sprintId);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("L'identifiant du sprint est invalide : " + sprintId);
    }
    return jiraApiClient.calculateBurnupForSprint(sprintId);
}


    @GetMapping("/{sprintId}")
    public SprintInfoDTO getSprintInfo(@PathVariable String sprintId) throws Exception {
        System.out.println("Controller getSprintInfo appelé pour le sprint : " + sprintId);
        return jiraApiClient.getSprintInfo(sprintId);
    }

    @GetMapping(value ="/projects/{projectKey}/sprints", produces = "application/json")
    public ResponseEntity<List<SprintInfoDTO>> getSprintsForProject(@PathVariable String projectKey) {
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
            return ResponseEntity.ok(sprints);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/sprints/{id}/full-info")    public SprintInfoDTO getFullSprintInfo(@PathVariable String id) throws Exception {
        JiraApiClient.SprintCommitInfo commitInfo = jiraApiClient.analyseTicketsSprint(id);
        System.out.println("analyseTicketsSprint terminée");
        SprintKpiInfo kpiInfo = sprintStatsService.computeKpis(commitInfo);
        System.out.println("computeKpis terminée");

        SprintInfoDTO sprintInfoDTO = jiraApiClient.getSprintInfo(id);
        sprintInfoDTO.setCommitInfo(commitInfo);
        sprintInfoDTO.setSprintKpiInfo(kpiInfo);

        return sprintInfoDTO;
    }


}
