package fr.agile;

import fr.agile.dto.AvancementHistorique;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Data
public class IssueChangelogData {

    private final List<AvancementHistorique> avancementHistorique;
    private final NavigableMap<LocalDateTime, String> statutAvantDateMap;
    private final List<AvancementHistorique> sprintHistorique;

    public IssueChangelogData(List<AvancementHistorique> avancementHistorique, List<AvancementHistorique> sprintHistorique) {
        this.avancementHistorique = avancementHistorique;
        this.sprintHistorique = sprintHistorique;
        this.statutAvantDateMap = new TreeMap<>();
    }

    public IssueChangelogData(List<AvancementHistorique> avancementHistorique, NavigableMap<LocalDateTime, String> statutAvantDateMap, List<AvancementHistorique> sprintHistorique) {
        this.avancementHistorique = avancementHistorique;
        this.statutAvantDateMap = statutAvantDateMap;
        this.sprintHistorique = sprintHistorique;
    }

    public String getLastStatusBefore(ZonedDateTime dateTime) {
        LocalDateTime localDateTime = dateTime.toLocalDateTime();
        Map.Entry<LocalDateTime, String> entry = statutAvantDateMap.floorEntry(localDateTime);
        return entry != null ? entry.getValue() : null;
    }

    public List<AvancementHistorique> getAvancementHistorique() {
        return avancementHistorique;
    }

    public List<AvancementHistorique> getSprintHistorique() {
        return sprintHistorique;
    }
}

