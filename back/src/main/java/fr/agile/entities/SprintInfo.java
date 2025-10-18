package fr.agile.entities;

import java.time.ZonedDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "sprint_info", schema = "agile")
public class SprintInfo {

    @Id
    private Long id;

    private String name;
    private String state;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime completeDate;
    private Long originBoardId;

    @Column(name = "velocity")
    private Double velocity;
    @Column(name = "velocity_start")
    private Double velocityStart;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "burnup_data_id")
    private BurnupData burnupData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public ZonedDateTime getCompleteDate() {
        return completeDate;
    }

    public void setCompleteDate(ZonedDateTime completeDate) {
        this.completeDate = completeDate;
    }

    public Long getOriginBoardId() {
        return originBoardId;
    }

    public void setOriginBoardId(Long originBoardId) {
        this.originBoardId = originBoardId;
    }

    public BurnupData getBurnupData() {
        return burnupData;
    }

    public void setBurnupData(BurnupData burnupData) {
        this.burnupData = burnupData;
    }

    public Double getVelocity() { return velocity; }
    public void setVelocity(Double velocity) { this.velocity = velocity; }

    public Double getVelocityStart() {
        return velocityStart;
    }

    public void setVelocityStart(Double velocityStart) {
        this.velocityStart = velocityStart;
    }
}
