package fr.agile.service;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.mapper.Mapper;
import fr.agile.model.dto.EventDTO;
import fr.agile.repository.DevelopperRepository;
import fr.agile.repository.EventRepository;

@Component
public class EventService {
    @Autowired
    EventRepository eventRepository;
    @Autowired
    DevelopperRepository dr;

    @Autowired
    private Mapper mapper;

    @Transactional
    public EventDTO createEvent(EventDTO eventDTO) {
        Developper developper = dr.findById(eventDTO.getDevelopper().getIdResource()).get();
        Event e = mapper.toEvent(eventDTO);
        e.setDevelopper(developper);
        return(mapper.toDto(eventRepository.save(e)));
    }



    public Double getTotalJoursConges(Long developperId) {
        List<Object[]> result = eventRepository.getTotalJoursCongesByDevelopperId(developperId);

        if (!result.isEmpty()) {
            Object[] row = result.get(0); // Comme on groupe par `developperId`, il n'y a qu'un seul résultat.
            return (Double) row[1]; // On récupère le total des jours de congé calculés
        }
        return 0.0; // Si aucun résultat trouvé
    }

    public List<Event> getAll() {
        return eventRepository.findAll();
    }

    public boolean deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            return false;
        }
        eventRepository.deleteById(id);
        return true;
    }

}
