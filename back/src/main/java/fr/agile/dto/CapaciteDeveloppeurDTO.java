package fr.agile.dto;

public class CapaciteDeveloppeurDTO {
    private Long idDevelopper;
    private String nom;
    private String prenom;
    private double capaciteStoryPoints;

    public Long getIdDevelopper() {
        return idDevelopper;
    }

    public void setIdDevelopper(Long idDevelopper) {
        this.idDevelopper = idDevelopper;
    }

    public double getCapaciteStoryPoints() {
        return capaciteStoryPoints;
    }

    public void setCapaciteStoryPoints(double capaciteStoryPoints) {
        this.capaciteStoryPoints = capaciteStoryPoints;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }
}