package fr.agile.controller;

import java.util.List;
import java.util.Map;

import fr.agile.dto.CapaciteDevProchainSprintDTO;
import fr.agile.dto.SprintInfoDTO;
import fr.agile.entities.SprintDevelopperAvailability;
import fr.agile.mapper.SprintMapper;
import fr.agile.entities.SprintInfo;
import fr.agile.repository.DevelopperRepository;
import fr.agile.repository.SprintDevelopperAvailabilityRepository;
import fr.agile.repository.SprintInfoRepository;
import fr.agile.service.SprintCapacityService;
import fr.agile.service.SprintService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sprints")
@CrossOrigin(origins = "*") // Autorise les appels depuis Angular
public class SprintController {

    private final SprintService sprintService;
    private final SprintCapacityService sprintCapacityService;


    public SprintController(SprintService sprintService,SprintCapacityService sprintCapacityService ) {
        this.sprintService = sprintService;
        this.sprintCapacityService = sprintCapacityService;
    }

    @PostMapping
    public ResponseEntity<SprintInfoDTO> createSprint(@RequestBody SprintInfoDTO dto) {
        SprintInfo saved = sprintService.saveSprint(SprintMapper.toEntity(dto));
        return ResponseEntity.ok(SprintMapper.toDTO(saved));
    }

//    @GetMapping("/{id}")
//    public ResponseEntity<SprintInfoDTO> getSprint(@PathVariable Long id) {
//        SprintInfo sprint = sprintService.getById(id);
//        return ResponseEntity.ok(SprintMapper.toDTO(sprint));
//    }

    @GetMapping("/boards/{boardId}/sprints/{nextSprintId}/developers/{devId}/capacity")
    public ResponseEntity<CapaciteDevProchainSprintDTO> getDevCapacityAndCarryover(@PathVariable Long boardId, @PathVariable Long nextSprintId, @PathVariable Long devId
    ) throws Exception {
        return ResponseEntity.ok(sprintCapacityService.getCapaciteEtRestePourDev(boardId, nextSprintId, devId));
    }

    @GetMapping("/boards/{boardId}/sprints/{nextSprintId}/developers/capacity")
    public ResponseEntity<List<CapaciteDevProchainSprintDTO>> getAllDevCapacityAndCarryover(@PathVariable Long boardId, @PathVariable Long nextSprintId) throws Exception {
        return ResponseEntity.ok(sprintCapacityService.getCapaciteEtRestePourTousDevsOptim(boardId, nextSprintId));
    }

    @GetMapping("/{sprintId}/developers/{devId}/availability")
    public ResponseEntity<Double> getAvailability(@PathVariable Long sprintId, @PathVariable Long devId) {
        double percent = sprintCapacityService.getAvailabilityPercent(sprintId, devId);
        return ResponseEntity.ok(percent);
    }

    @PutMapping("/{sprintId}/developers/{devId}/availability")
    public ResponseEntity<Void> upsertAvailability(@PathVariable Long sprintId, @PathVariable Long devId, @RequestParam("percent") double percent) {
        sprintCapacityService.upsertAvailability(sprintId, devId, percent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sprintId}/availability")
    public ResponseEntity<List<Map<String,Object>>> listAvailability(@PathVariable Long sprintId) {
        var list = sprintCapacityService.listAvailability(sprintId);
        return ResponseEntity.ok(list);
    }
}
