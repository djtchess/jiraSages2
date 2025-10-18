package fr.agile.dto;

import fr.agile.entities.BurnupPoint;

public class BurnupPointDTO {
    private String date;
    private Double donePoints;
    private Double capacity;
    private Double capacityJH;
    private Double velocity;

    public BurnupPointDTO() {
    }

    public BurnupPointDTO(String date, Double donePoints, Double capacity, Double capacityJH, Double velocity) {
        this.date = date;
        this.donePoints = donePoints;
        this.capacity = capacity;
        this.capacityJH = capacityJH;
        this.velocity = velocity;
    }

    public BurnupPointDTO(BurnupPoint burnupPoint) {
        if (burnupPoint != null) {
            this.date = burnupPoint.getDate();
            this.donePoints = burnupPoint.getDonePoints();
            this.capacity = burnupPoint.getCapacity();
            this.capacityJH = burnupPoint.getCapacityJH();
            this.velocity = burnupPoint.getVelotice();
        }
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Double getDonePoints() {
        return donePoints;
    }

    public void setDonePoints(Double donePoints) {
        this.donePoints = donePoints;
    }

    public Double getCapacity() {
        return capacity;
    }

    public void setCapacity(Double capacity) {
        this.capacity = capacity;
    }

    public Double getCapacityJH() {
        return capacityJH;
    }

    public void setCapacityJH(Double capacityJH) {
        this.capacityJH = capacityJH;
    }

    public Double getVelocity() {
        return velocity;
    }

    public void setVelocity(Double velocity) {
        this.velocity = velocity;
    }
}
