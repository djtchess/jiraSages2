package fr.agile.model.dto;

import java.sql.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DevelopperDTO {

    private Long idResource;

    private String nomResource;

    private String prenomResource;

    private Date dateDebut;
    private Date dateFin;

    private List<EventDTO> events;

}