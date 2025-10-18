package fr.agile.dto;

import java.time.ZonedDateTime;

import fr.agile.JiraApiClient;

public class SprintInfoDTO {
    private Long id;
    private String name;
    private String state;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime completeDate;
    private Long originBoardId;

    private BurnupDataDTO burnupData;
    private JiraApiClient.SprintCommitInfo sprintCommitInfo;
    private SprintKpiInfo sprintKpiInfo;

    private Double velocity;
    private Double velocityStart;

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

    public BurnupDataDTO getBurnupData() {
        return burnupData;
    }

    public void setBurnupData(BurnupDataDTO burnupData) {
        this.burnupData = burnupData;
    }

    public JiraApiClient.SprintCommitInfo getSprintCommitInfo() {
        return sprintCommitInfo;
    }

    public void setCommitInfo(JiraApiClient.SprintCommitInfo sprintCommitInfo) {
        this.sprintCommitInfo = sprintCommitInfo;
    }

    public SprintKpiInfo getSprintKpiInfo() {
        return sprintKpiInfo;
    }

    public void setSprintKpiInfo(SprintKpiInfo sprintKpiInfo) {
        this.sprintKpiInfo = sprintKpiInfo;
    }

    public Double getVelocity() {
        return velocity;
    }

    public void setVelocity(Double velocity) {
        this.velocity = velocity;
    }

    public Double getVelocityStart() {
        return velocityStart;
    }

    public void setVelocityStart(Double velocityStart) {
        this.velocityStart = velocityStart;
    }
}
