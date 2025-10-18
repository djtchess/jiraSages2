package fr.agile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

import fr.agile.entities.Developper;

public interface DevelopperRepository extends JpaRepository<Developper, Long> {
}