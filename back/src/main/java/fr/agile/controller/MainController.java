
package fr.agile.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import fr.agile.mapper.Mapper;
import fr.agile.model.dto.DevelopperDTO;
import fr.agile.model.dto.EventDTO;
import fr.agile.repository.DevelopperRepository;
import fr.agile.service.DevelopperService;
import fr.agile.service.EventService;


@RestController
//@RequestMapping(value="/agile")
@CrossOrigin(origins = "http://localhost:4200")
public class MainController {

    @Autowired
    DevelopperService ds;

    @Autowired
    EventService es;

    @RequestMapping("/api")
    @ResponseBody
    String home() {
        return "Welcome!";
    }

    @RequestMapping("/api/developpers")
    List<DevelopperDTO> developpers() {
        return ds.getAllDevelopper();
    }

    @PostMapping("/api/events/create")
    public ResponseEntity<EventDTO> createEvent(@RequestBody EventDTO eventDTO) {
        EventDTO saved = es.createEvent(eventDTO);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/api/events/totalJoursConges/{developperId}")
    public ResponseEntity<Double> getTotalJoursConges(@PathVariable Long developperId) {
        Double totalJoursConges = es.getTotalJoursConges(developperId);
        return ResponseEntity.ok(totalJoursConges);
    }

    @DeleteMapping("/api/events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable("id") Long id) {
        boolean deleted = es.deleteEvent(id);
        return deleted
                ? ResponseEntity.noContent().build()     // 204 No Content
                : ResponseEntity.notFound().build();     // 404 Not Found
    }

}