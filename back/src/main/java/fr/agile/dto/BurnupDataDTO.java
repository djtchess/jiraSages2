package fr.agile.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import fr.agile.entities.BurnupData;
import fr.agile.utils.BurnupUtils;

public class BurnupDataDTO {
    private List<BurnupPointDTO> points;
    private Double totalStoryPoints;
    private Double totalJH;
    private Double velocity;

    public BurnupDataDTO() {
    }

    public BurnupDataDTO(List<BurnupPointDTO> points, Double totalStoryPoints) {
        this.points = points;
        this.totalStoryPoints = totalStoryPoints;

        if (points != null && !points.isEmpty()) {
            // Total JH = dernier point (inchangé)
            this.totalJH = BurnupUtils.roundToTwoDecimals(points.get(points.size() - 1).getCapacityJH());

            LocalDate today = LocalDate.now();

            // Récupérer toutes les dates <= aujourd'hui
            List<BurnupPointDTO> pastOrToday = points.stream()
                    .filter(p -> {
                        try {
                            return LocalDate.parse(p.getDate()).compareTo(today) <= 0;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();

            BurnupPointDTO selected;
            if (pastOrToday.size() >= 2) {
                // avant-dernier point par rapport à aujourd'hui
                selected = pastOrToday.get(pastOrToday.size() - 2);
            } else if (pastOrToday.size() == 1) {
                // un seul point dispo → on le prend
                selected = pastOrToday.get(0);
            } else {
                // aucun point <= today → fallback dernier point global
                selected = points.get(points.size() - 1);
            }

            this.velocity = BurnupUtils.roundToTwoDecimals(selected.getVelocity());
        } else {
            this.totalJH = 0.0;
            this.velocity = 0.0;
        }
    }



    public BurnupDataDTO(BurnupData burnupData) {
        if (burnupData != null) {
            this.totalStoryPoints = burnupData.getTotalStoryPoints();
            this.totalJH = burnupData.getTotalJH();
            this.velocity = BurnupUtils.roundToTwoDecimals(burnupData.getVelocite());

            if (burnupData.getPoints() != null) {
                this.points = burnupData.getPoints()
                        .stream()
                        .map(BurnupPointDTO::new)
                        .collect(Collectors.toList());
            }
        }
    }

    public List<BurnupPointDTO> getPoints() {
        return points;
    }

    public void setPoints(List<BurnupPointDTO> points) {
        this.points = points;
    }

    public Double getTotalStoryPoints() {
        return totalStoryPoints;
    }

    public void setTotalStoryPoints(Double totalStoryPoints) {
        this.totalStoryPoints = totalStoryPoints;
    }

    public Double getTotalJH() {
        return totalJH;
    }

    public void setTotalJH(Double totalJH) {
        this.totalJH = totalJH;
    }

    public Double getVelocity() {
        return velocity;
    }

    public void setVelocity(Double velocity) {
        this.velocity = velocity;
    }
}
