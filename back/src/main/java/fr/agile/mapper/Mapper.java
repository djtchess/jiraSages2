package fr.agile.mapper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import fr.agile.entities.Developper;
import fr.agile.entities.Event;
import fr.agile.model.dto.DevelopperDTO;
import fr.agile.model.dto.EventDTO;

@Component
public class Mapper {

    public DevelopperDTO toDto(Developper developper){
        return  new DevelopperDTO(developper.getId(), developper.getNomDevelopper(), developper.getPrenomDevelopper(), developper.getDateDebut(), developper.getDateFin(), new ArrayList<>());
    }

    public Developper toDevelopper(DevelopperDTO developperDTO){
        return new Developper(developperDTO.getIdResource(), developperDTO.getNomResource(), developperDTO.getPrenomResource(), null, null);
    }

    public EventDTO toDto(Event event){
        return  new EventDTO(event.getId(), event.getLibelleEvent(), event.getDateDebutEvent(),event.getDateFinEvent(), event.getIsMatin(), event.getIsApresMidi(), event.getIsJournee(), toDto(event.getDevelopper()));
    }

    public Event toEvent(EventDTO eventDTO){
        return new Event(eventDTO.getIdEvent(), eventDTO.getLibelleEvent(), eventDTO.getDateDebutEvent(), eventDTO.getDateFinEvent(), eventDTO.getIsMatin(), eventDTO.getIsApresMidi(), eventDTO.getIsJournee(), toDevelopper(eventDTO.getDevelopper()));
    }
}
