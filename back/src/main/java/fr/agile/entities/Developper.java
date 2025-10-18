package fr.agile.entities;

import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "developper", schema = "agile")
public class Developper {
    @Id
    @Column(name = "id_developper", nullable = false)
    private Long id;

    @Column(name = "nom_developper", nullable = false, length = Integer.MAX_VALUE)
    private String nomDevelopper;

    @Column(name = "prenom_developper", nullable = false, length = Integer.MAX_VALUE)
    private String prenomDevelopper;

    @Column(name = "date_debut")
    private Date dateDebut;

    @Column(name = "date_fin")
    private Date dateFin;

}