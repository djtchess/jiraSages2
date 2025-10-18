package fr.agile.entities;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "event", schema = "agile")
public class Event {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column(name = "id_event", nullable = false)
    private Long id;

    @Column(name = "libelle_event", nullable = true)
    private String libelleEvent;

    @Column(name = "date_debut_event", nullable = false)
    private LocalDate dateDebutEvent;

    @Column(name = "date_fin_event", nullable = false)
    private LocalDate dateFinEvent;

    @Column(name = "is_matin", nullable = false)
    private Boolean isMatin;

    @Column(name = "is_apres_midi", nullable = false)
    private Boolean isApresMidi;

    @Column(name = "is_journee", nullable = false)
    private Boolean isJournee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "developper_id", nullable = false)
    private Developper developper;

}