package fr.agile.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import fr.agile.entities.SprintInfo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SprintInfoRepository extends JpaRepository<SprintInfo, Long> {

    List<SprintInfo> findTop5ByOriginBoardIdAndStateInAndVelocityIsNotNullOrderByStartDateDesc(Long originBoardId, Collection<String> states);
    Page<SprintInfo> findByOriginBoardIdAndStateInAndVelocityIsNotNull(Long boardId, Collection<String> states, Pageable pageable);

    @Query("update SprintInfo s set s.velocity = :velocity where s.id = :sprintId")
    void setVelocityForSprint(Long sprintId, Double velocity);

    Optional<SprintInfo> findFirstByOriginBoardIdAndStateIgnoreCaseOrderByStartDateDesc(Long boardId, String state);


}
