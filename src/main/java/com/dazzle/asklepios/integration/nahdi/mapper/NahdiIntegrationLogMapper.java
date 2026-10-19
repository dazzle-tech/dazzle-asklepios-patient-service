package com.dazzle.asklepios.integration.nahdi.mapper;

import com.dazzle.asklepios.integration.nahdi.domain.NahdiIntegrationLog;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiIntegrationLogDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper between {@link NahdiIntegrationLog} and {@link NahdiIntegrationLogDTO}.
 */
@Component
public class NahdiIntegrationLogMapper {

    public NahdiIntegrationLogDTO toDto(NahdiIntegrationLog entity) {
        if (entity == null) {
            return null;
        }
        return new NahdiIntegrationLogDTO(
                entity.getId(),
                entity.getEndpoint(),
                entity.getRequestPayload(),
                entity.getResponsePayload(),
                entity.getStatus(),
                entity.getOrderNumber(),
                entity.getCreatedDate()
        );
    }

    /**
     * {@code id} and {@code createdDate} are managed by persistence and auditing, so they are not copied.
     */
    public NahdiIntegrationLog toEntity(NahdiIntegrationLogDTO dto) {
        if (dto == null) {
            return null;
        }
        NahdiIntegrationLog entity = new NahdiIntegrationLog();
        entity.setEndpoint(dto.endpoint());
        entity.setRequestPayload(dto.requestPayload());
        entity.setResponsePayload(dto.responsePayload());
        entity.setStatus(dto.status());
        entity.setOrderNumber(dto.orderNumber());
        return entity;
    }
}
