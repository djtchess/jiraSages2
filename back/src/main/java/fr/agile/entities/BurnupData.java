package fr.agile.entities;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "burnup_data", schema = "agile")
public class BurnupData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "burnupData", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BurnupPoint> points = new ArrayList<>();

    @Column(name = "total_story_points")
    private Double totalStoryPoints;

    public BurnupData() {}

    // Utilitaire pour ajouter un point
    public void addPoint(BurnupPoint point) {
        point.setBurnupData(this);
        this.points.add(point);
    }

    // Getters et setters

    public Long getId() { return id; }

    public List<BurnupPoint> getPoints() { return points; }
    public void setPoints(List<BurnupPoint> points) {
        this.points.clear();
        if (points != null) {
            for (BurnupPoint point : points) {
                addPoint(point);
            }
        }
    }

    public Double getTotalStoryPoints() { return totalStoryPoints; }
    public void setTotalStoryPoints(Double totalStoryPoints) { this.totalStoryPoints = totalStoryPoints; }

    public Double getVelocite() {
        return points != null && !points.isEmpty() ? points.get(points.size() - 1).getVelotice() : 0.0;
    }

    public Double getTotalJH() {
        return points != null && !points.isEmpty() ? points.get(points.size() - 1).getCapacityJH() : 0.0;
    }
}
