package fr.agile.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "burnup_point", schema = "agile")
public class BurnupPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String date;

    @Column(name = "done_points")
    private Double donePoints;

    private Double capacity;

    @Column(name = "capacity_jh")
    private Double capacityJH;

    private Double velotice;

    @ManyToOne
    @JoinColumn(name = "burnup_data_id")
    private BurnupData burnupData;

    public BurnupPoint() {}

    public BurnupPoint(String date, Double donePoints, Double capacity, Double capacityJH) {
        this.date = date;
        this.donePoints = donePoints;
        this.capacity = capacity;
        this.capacityJH = capacityJH;
        this.velotice = (capacityJH != null && capacityJH != 0) ? donePoints / capacityJH : 0.0;
    }

    // Getters et setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Double getDonePoints() { return donePoints; }
    public void setDonePoints(Double donePoints) { this.donePoints = donePoints; }

    public Double getCapacity() { return capacity; }
    public void setCapacity(Double capacity) { this.capacity = capacity; }

    public Double getCapacityJH() { return capacityJH; }
    public void setCapacityJH(Double capacityJH) { this.capacityJH = capacityJH; }

    public Double getVelotice() { return velotice; }
    public void setVelotice(Double velotice) { this.velotice = velotice; }

    public BurnupData getBurnupData() { return burnupData; }
    public void setBurnupData(BurnupData burnupData) { this.burnupData = burnupData; }
}
