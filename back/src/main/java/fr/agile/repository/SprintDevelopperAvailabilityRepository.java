// src/main/java/fr/agile/repository/SprintDevelopperAvailabilityRepository.java
package fr.agile.repository;

import fr.agile.entities.SprintDevelopperAvailability;
import fr.agile.entities.SprintInfo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintDevelopperAvailabilityRepository extends JpaRepository<SprintDevelopperAvailability, Long> {
    Optional<SprintDevelopperAvailability> findBySprint_IdAndDevelopper_Id(Long sprintId, Long developperId);
    List<SprintDevelopperAvailability> findBySprint_Id(Long sprintId);

   }
