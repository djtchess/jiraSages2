package fr.agile.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

import fr.agile.entities.JoursFeries;

@Repository
public interface JoursFeriesRepository extends JpaRepository<JoursFeries, Long> {

    // Récupère tous les jours fériés entre deux dates
    List<JoursFeries> findByDateFerieBetween(Date start, Date end);

    // Récupère les jours fériés pour une date spécifique
    List<JoursFeries> findByDateFerie(Date date);
}
