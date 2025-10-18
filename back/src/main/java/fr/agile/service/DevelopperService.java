package fr.agile.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.mapper.Mapper;
import fr.agile.model.dto.DevelopperDTO;
import fr.agile.repository.DevelopperRepository;
import fr.agile.repository.EventRepository;

@Component
public class DevelopperService {
    @Autowired
    DevelopperRepository dr;

    @Autowired
    EventRepository er;

    @Autowired
    private Mapper mapper;

    public List<DevelopperDTO> getAllDevelopper() {
        Iterable<Developper> all = dr.findAll();
        List<DevelopperDTO> developperList = new ArrayList<DevelopperDTO>();
        all.forEach(x->{
            DevelopperDTO developperDTO = mapper.toDto(x);
            List<Event> events = er.findByDevelopper_Id(x.getId().longValue());
            events.forEach(event->developperDTO.getEvents().add((mapper.toDto(event))));
            developperList.add(developperDTO);
        });
        return developperList;
    }

    public List<Developper> getAll() {
        return  dr.findAll();
    }

}
