package fr.agile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpicVersionSprintDurationDTO {
    private String version;
    private String epicKey;
    private String epicName;
    private Long sprintId;
    private String sprintName;
    private Double sprintDurationDays;
    private Double storyPoints;
}
