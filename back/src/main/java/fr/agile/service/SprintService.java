package fr.agile.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Optional;

import fr.agile.dto.SprintInfoDTO;
import fr.agile.entities.SprintInfo;
import fr.agile.mapper.SprintMapper;
import fr.agile.repository.SprintInfoRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SprintService {

    private final SprintInfoRepository sprintInfoRepository;

    public SprintService(SprintInfoRepository sprintInfoRepository) {
        this.sprintInfoRepository = sprintInfoRepository;
    }


    @Transactional
    public SprintInfoDTO saveSprint(SprintInfoDTO sprintInfoDTO) {
        SprintInfo savedSprint = saveSprint(SprintMapper.toEntity(sprintInfoDTO));
        return SprintMapper.toDTO(savedSprint);
    }

    @Transactional
    public SprintInfo saveSprint(SprintInfo sprint) {
        return sprintInfoRepository.findById(sprint.getId())
                .map(existing -> {
                    // Conserver le velocityStart existant s’il est déjà saisi
                    if (sprint.getVelocityStart() == null) {
                        sprint.setVelocityStart(existing.getVelocityStart());
                    }
                    // Ne PAS écraser d’autres champs calculés ici (principe: méthode idempotente)
                    return sprintInfoRepository.save(sprint);
                })
                .orElseGet(() -> sprintInfoRepository.save(sprint));
    }

    public Optional<SprintInfo> getById(Long id) {
        return sprintInfoRepository.findById(id);
    }

    @Transactional
    public void saveOrUpdateVelocity(Long sprintId, double velocity) {
        SprintInfo si = sprintInfoRepository.findById(sprintId).orElse(null);
        if (si == null) return;
        si.setVelocity(velocity);
        sprintInfoRepository.save(si);
    }

    @Transactional
    public void saveOrUpdateVelocityStart(Long sprintId, double velocity) {
        SprintInfo si = sprintInfoRepository.findById(sprintId).orElse(null);
        if (si == null) return;
        si.setVelocityStart(velocity);
        sprintInfoRepository.save(si);
    }

    /** Moyenne des 5 derniers sprints CLOSED avec velocity non nulle. */
    @Transactional(readOnly = true)
    public Optional<Double> getAvgVelocityLastClosed(Long boardId, int limit) {
        List<SprintInfo> last = sprintInfoRepository.findTop5ByOriginBoardIdAndStateInAndVelocityIsNotNullOrderByStartDateDesc(
                boardId, List.of("closed")
        );
        return getAvgVelocity(last);
    }

    private static Optional<Double> getAvgVelocity(List<SprintInfo> last) {
        if (last.isEmpty()) return Optional.empty();

        double avg = last.stream()
                .map(SprintInfo::getVelocity)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);

        if (Double.isNaN(avg)) return Optional.empty();

        double rounded = BigDecimal.valueOf(avg)
                .setScale(2, RoundingMode.HALF_UP)   // ← 2 décimales
                .doubleValue();

        return Optional.of(rounded);
    }

    /** Moyenne des 5 derniers sprints CLOSED et ACTIVE avec velocity non nulle. */
    @Transactional(readOnly = true)
    public Optional<Double> getAvgVelocityLastClosedAndActive(Long boardId, int limit) {
        var page = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "startDate"));
        var last = sprintInfoRepository
                .findByOriginBoardIdAndStateInAndVelocityIsNotNull(boardId, List.of("closed","active"), page)
                .getContent();
        if (last.isEmpty()) return Optional.empty();
        double avg = last.stream().map(SprintInfo::getVelocity)
                .mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double rounded = new BigDecimal(avg).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return Optional.of(rounded);
    }

    @Transactional(readOnly = true)
    public Optional<Double> getVelocityStartSprint(Long id) {
        return sprintInfoRepository.findById(id)
                .map(SprintInfo::getVelocityStart);
    }
}
