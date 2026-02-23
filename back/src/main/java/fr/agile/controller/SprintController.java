package fr.agile.controller;

import java.util.List;
import java.util.Map;

import fr.agile.dto.CapaciteDevProchainSprintDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.service.SprintCapacityService;
import fr.agile.service.SprintService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sprints")
@CrossOrigin(origins = "*") // Autorise les appels depuis Angular
@Validated
public class SprintController {

    private final SprintService sprintService;
    private final SprintCapacityService sprintCapacityService;

    public SprintController(SprintService sprintService, SprintCapacityService sprintCapacityService) {
        this.sprintService = sprintService;
        this.sprintCapacityService = sprintCapacityService;
    }

    @PostMapping
    public ResponseEntity<SprintInfoDTO> createSprint(@Valid @RequestBody SprintInfoDTO dto) {
        return ResponseEntity.ok(sprintService.saveSprint(dto));
    }

    @GetMapping("/boards/{boardId}/sprints/{nextSprintId}/developers/{devId}/capacity")
    public ResponseEntity<CapaciteDevProchainSprintDTO> getDevCapacityAndCarryover(
            @PathVariable @Positive Long boardId,
            @PathVariable @Positive Long nextSprintId,
            @PathVariable("devId") @Positive Long developerId) {
        return ResponseEntity.ok(sprintCapacityService.getCapaciteEtRestePourDev(boardId, nextSprintId, developerId));
    }

    @GetMapping("/boards/{boardId}/sprints/{nextSprintId}/developers/capacity")
    public ResponseEntity<List<CapaciteDevProchainSprintDTO>> getAllDevCapacityAndCarryover(
            @PathVariable @Positive Long boardId,
            @PathVariable @Positive Long nextSprintId) {
        return ResponseEntity.ok(sprintCapacityService.getCapaciteEtRestePourTousDevsOptim(boardId, nextSprintId));
    }

    @GetMapping("/{sprintId}/developers/{devId}/availability")
    public ResponseEntity<Double> getAvailability(@PathVariable @Positive Long sprintId,
                                                  @PathVariable("devId") @Positive Long developerId) {
        double percent = sprintCapacityService.getAvailabilityPercent(sprintId, developerId);
        return ResponseEntity.ok(percent);
    }

    @PutMapping("/{sprintId}/developers/{devId}/availability")
    public ResponseEntity<Void> upsertAvailability(@PathVariable @Positive Long sprintId,
                                                   @PathVariable("devId") @Positive Long developerId,
                                                   @RequestParam("percent")
                                                   @DecimalMin(value = "0.0")
                                                   @DecimalMax(value = "100.0")
                                                   double percent) {
        sprintCapacityService.upsertAvailability(sprintId, developerId, percent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sprintId}/availability")
    public ResponseEntity<List<Map<String, Object>>> listAvailability(@PathVariable @Positive Long sprintId) {
        var list = sprintCapacityService.listAvailability(sprintId);
        return ResponseEntity.ok(list);
    }
}
