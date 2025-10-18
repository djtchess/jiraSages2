package fr.agile.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import fr.agile.entities.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
	@Query("from Event e where not(e.dateFinEvent < :from and e.dateDebutEvent > :to)")
	public List<Event> findBetween(@Param("from") LocalDateTime start, @Param("to") @DateTimeFormat(iso=ISO.DATE_TIME) LocalDateTime end);

	public List<Event> findByDevelopper_Id(Long id);

	@Query(value = "SELECT e.developper_id, " +
			"SUM(CASE " +
			"WHEN e.is_journee = true AND EXTRACT(DOW FROM e.date_debut_event) NOT IN (0, 6) " +
			"AND NOT EXISTS (SELECT 1 FROM agile.jours_feries jf WHERE jf.date_ferie = e.date_debut_event) THEN " +
			"(e.date_fin_event - e.date_debut_event) + 1 " +
			"WHEN e.is_matin = true AND e.is_apres_midi = false AND EXTRACT(DOW FROM e.date_debut_event) NOT IN (0, 6) " +
			"AND NOT EXISTS (SELECT 1 FROM agile.jours_feries jf WHERE jf.date_ferie = e.date_debut_event) THEN " +
			"0.5 " +
			"WHEN e.is_apres_midi = true AND e.is_matin = false AND EXTRACT(DOW FROM e.date_debut_event) NOT IN (0, 6) " +
			"AND NOT EXISTS (SELECT 1 FROM agile.jours_feries jf WHERE jf.date_ferie = e.date_debut_event) THEN " +
			"0.5 " +
			"ELSE 0 END) AS total_jours_conges " +
			"FROM agile.event e " +
			"WHERE e.developper_id = :developperId " +
			"GROUP BY e.developper_id", nativeQuery = true)
	public List<Object[]> getTotalJoursCongesByDevelopperId(@Param("developperId") Long developperId);


	@Query("SELECT e FROM Event e WHERE e.dateDebutEvent <= :end AND e.dateFinEvent >= :start")
	List<Event> findByDateRange(@Param("start") Date start, @Param("end") Date end);

	List<Event> findByDevelopper_IdAndDateDebutEventLessThanEqualAndDateFinEventGreaterThanEqual(
			Long developperId, LocalDate end, LocalDate start);
}