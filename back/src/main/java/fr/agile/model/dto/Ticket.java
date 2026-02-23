package fr.agile.model.dto;

import java.time.LocalDate;
import java.util.List;

import fr.agile.IssueChangelogData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Builder
@EqualsAndHashCode(of = "ticketKey")
public class Ticket {

    private String ticketKey;
    private String url;
    private String assignee;
    private String status;
    private Double storyPoints;
    private Double storyPointsRealiseAvantSprint;
    private Double avancement;
    private String engagementSprint;
    private String type;
    private String summary;
    private String versionCorrigee;
    private String epicKey;
    private String epicName;
    private LocalDate createdDate;
    private List<String> sprintIds;
    private boolean devTermineAvantSprint;

    /**
     * Changelog mis en mémoire ; ignoré lors de la sérialisation JSON
     * (et retiré du `toString` pour éviter des logs immenses).
     */
    @JsonIgnore
    @ToString.Exclude
    private IssueChangelogData changelog;

    public double remainingSp() {            // null-safe
        double spTotal   = getStoryPoints()              != null ? getStoryPoints()              : 0.0;
        double spAvant   = getStoryPointsRealiseAvantSprint() != null ? getStoryPointsRealiseAvantSprint() : 0.0;
        return Math.max(0.0, spTotal - spAvant);              // on évite les valeurs négatives
    }

}


