package fr.agile.dto;

import java.util.List;

import lombok.Data;

@Data
public class CapaciteDevProchainSprintDTO {
    private Long   idDevelopper;
    private String nom;
    private String prenom;

    // Capacité brute calculée pour le nouveau sprint
    private double capaciteNouveauSprint;

    // Reste à faire (tickets non terminés du sprint précédent) au démarrage du nouveau sprint
    private double resteAFaireSprintPrecedent;

    // Capacité nette = max(0, capacité - reste)
    private double capaciteNette;

    // Optionnel: pour transparence côté UI
    private List<String> ticketsRestantsKeys;
}
