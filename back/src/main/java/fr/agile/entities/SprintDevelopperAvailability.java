// src/main/java/fr/agile/entities/SprintDevelopperAvailability.java
package fr.agile.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sprint_developper_availability", schema = "agile",
        uniqueConstraints = @UniqueConstraint(name = "uniq_sprint_dev", columnNames = {"sprint_id", "id_developper"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SprintDevelopperAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Si vous avez déjà les entités SprintInfo & Developper, on mappe en ManyToOne :
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id")
    private SprintInfo sprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_developper")
    private Developper developper;

    @Column(name = "availability_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal availabilityPercent; // 0..100

    @Transient
    public double getFactor() {
        return availabilityPercent == null ? 1d : availabilityPercent.doubleValue() / 100d;
    }
}
