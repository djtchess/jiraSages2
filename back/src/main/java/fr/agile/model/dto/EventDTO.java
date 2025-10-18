package fr.agile.model.dto;

import java.sql.Date;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EventDTO {

    private Long idEvent;

    private String libelleEvent;

    private LocalDate dateDebutEvent;

    private LocalDate dateFinEvent;

    private Boolean isMatin;

    private Boolean isApresMidi;

    private Boolean isJournee;

    private DevelopperDTO developper;

}