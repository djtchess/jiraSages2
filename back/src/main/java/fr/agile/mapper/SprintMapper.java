package fr.agile.mapper;

import fr.agile.dto.*;
import fr.agile.entities.BurnupData;
import fr.agile.entities.BurnupPoint;
import fr.agile.entities.SprintInfo;

public class SprintMapper {

    public static SprintInfoDTO toDTO(SprintInfo entity) {
        if (entity == null) return null;

        SprintInfoDTO dto = new SprintInfoDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setState(entity.getState());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setCompleteDate(entity.getCompleteDate());
        dto.setOriginBoardId(entity.getOriginBoardId());
        dto.setVelocity(entity.getVelocity());
        dto.setVelocityStart(entity.getVelocityStart());
        dto.setBurnupData(toDTO(entity.getBurnupData()));
        return dto;
    }

    public static SprintInfo toEntity(SprintInfoDTO dto) {
        if (dto == null) return null;

        SprintInfo entity = new SprintInfo();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setState(dto.getState());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setCompleteDate(dto.getCompleteDate());
        entity.setOriginBoardId(dto.getOriginBoardId());
        entity.setVelocity(dto.getBurnupData().getVelocity());
        entity.setVelocityStart(dto.getVelocityStart());
        entity.setBurnupData(toEntity(dto.getBurnupData()));
        return entity;
    }

    public static BurnupDataDTO toDTO(BurnupData entity) {
        if (entity == null) return null;

        BurnupDataDTO dto = new BurnupDataDTO();
        dto.setTotalStoryPoints(entity.getTotalStoryPoints());
        dto.setVelocity(entity.getVelocite());
        dto.setTotalJH(entity.getTotalJH());
        dto.setPoints(entity.getPoints().stream().map(SprintMapper::toDTO).toList());
        return dto;
    }

    public static BurnupData toEntity(BurnupDataDTO dto) {
        if (dto == null) return null;

        BurnupData entity = new BurnupData();
        entity.setPoints(dto.getPoints().stream().map(SprintMapper::toEntity).toList());
        entity.setTotalStoryPoints(dto.getTotalStoryPoints());
        return entity;
    }



    public static BurnupPointDTO toDTO(BurnupPoint entity) {
        BurnupPointDTO dto = new BurnupPointDTO();
        dto.setDate(entity.getDate());
        dto.setDonePoints(entity.getDonePoints());
        dto.setCapacity(entity.getCapacity());
        dto.setCapacityJH(entity.getCapacityJH());
        dto.setVelocity(entity.getVelotice());
        return dto;
    }

    public static BurnupPoint toEntity(BurnupPointDTO dto) {
        return new BurnupPoint(
            dto.getDate(),
            dto.getDonePoints(),
            dto.getCapacity(),
            dto.getCapacityJH()
        );
    }
}
