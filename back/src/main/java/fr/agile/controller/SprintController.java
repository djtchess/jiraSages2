package fr.agile.controller;

import java.util.List;
import java.util.Map;

import fr.agile.dto.CapaciteDevProchainSprintDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.service.SprintCapacityService;
import fr.agile.service.SprintService;

import org.springframework.http.ResponseEntity;
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
public class SprintController {

    private final SprintService sprintService;
    private final SprintCapacityService sprintCapacityService;

    public SprintController(SprintService sprintService, SprintCapacityService sprintCapacityService) {
        this.sprintService = sprintService;
        this.sprintCapacityService = sprintCapacityService;
    }

    @PostMapping
    public ResponseEntity<SprintInfoDTO> createSprint(@RequestBody SprintInfoDTO dto) {
        return ResponseEntity.ok(sprintService.saveSprint(dto));
    }

    @GetMapping("/boards/{boardId}/sprints/{nextSprintId}/developers/{devId}/capacity")
    public ResponseEntity<CapaciteDevProchainSprintDTO> getDevCapacityAndCarryover(@PathVariable Long boardId,
                                                                                     @PathVariable Long nextSprintId,
                                                                                     @PathVariable Long devId) throws Exception {
        return ResponseEntity.ok(sprintCapacityService.getCapaciteEtRestePourDev(boardId, nextSprintId, devId));
    }

    @GetMapping("/boards/{boardId}/sprints/{nextSprintId}/developers/capacity")
    public ResponseEntity<List<CapaciteDevProchainSprintDTO>> getAllDevCapacityAndCarryover(@PathVariable Long boardId,
                                                                                              @PathVariable Long nextSprintId) throws Exception {
        return ResponseEntity.ok(sprintCapacityService.getCapaciteEtRestePourTousDevsOptim(boardId, nextSprintId));
    }

    @GetMapping("/{sprintId}/developers/{devId}/availability")
    public ResponseEntity<Double> getAvailability(@PathVariable Long sprintId, @PathVariable Long devId) {
        double percent = sprintCapacityService.getAvailabilityPercent(sprintId, devId);
        return ResponseEntity.ok(percent);
    }

    @PutMapping("/{sprintId}/developers/{devId}/availability")
    public ResponseEntity<Void> upsertAvailability(@PathVariable Long sprintId,
                                                   @PathVariable Long devId,
                                                   @RequestParam("percent") double percent) {
        sprintCapacityService.upsertAvailability(sprintId, devId, percent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sprintId}/availability")
    public ResponseEntity<List<Map<String, Object>>> listAvailability(@PathVariable Long sprintId) {
        var list = sprintCapacityService.listAvailability(sprintId);
        return ResponseEntity.ok(list);
    }
}
