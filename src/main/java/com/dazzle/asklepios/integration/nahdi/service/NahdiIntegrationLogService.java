package com.dazzle.asklepios.integration.nahdi.service;

import com.dazzle.asklepios.integration.nahdi.domain.NahdiIntegrationLog;
import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiIntegrationLogDTO;
import com.dazzle.asklepios.integration.nahdi.mapper.NahdiIntegrationLogMapper;
import com.dazzle.asklepios.integration.nahdi.repository.NahdiIntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing {@link NahdiIntegrationLog}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NahdiIntegrationLogService {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiIntegrationLogService.class);

    private final NahdiIntegrationLogRepository nahdiIntegrationLogRepository;
    private final NahdiIntegrationLogMapper nahdiIntegrationLogMapper;

    /**
     * Records a single exchange with Nahdi. Runs in its own transaction so the log
     * survives a rollback of the calling flow.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NahdiIntegrationLogDTO save(NahdiIntegrationLogDTO nahdiIntegrationLogDTO) {
        LOG.debug("Request to save NahdiIntegrationLog endpoint={}, status={}",
                nahdiIntegrationLogDTO.endpoint(), nahdiIntegrationLogDTO.status());
        NahdiIntegrationLog entity = nahdiIntegrationLogMapper.toEntity(nahdiIntegrationLogDTO);
        entity = nahdiIntegrationLogRepository.save(entity);
        return nahdiIntegrationLogMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public Page<NahdiIntegrationLogDTO> findAll(NahdiIntegrationStatus status, Pageable pageable) {
        LOG.debug("Request to get all NahdiIntegrationLogs status={}", status);
        Page<NahdiIntegrationLog> page = status == null
                ? nahdiIntegrationLogRepository.findAll(pageable)
                : nahdiIntegrationLogRepository.findByStatus(status, pageable);
        return page.map(nahdiIntegrationLogMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<NahdiIntegrationLogDTO> findOne(Long id) {
        LOG.debug("Request to get NahdiIntegrationLog id={}", id);
        return nahdiIntegrationLogRepository.findById(id).map(nahdiIntegrationLogMapper::toDto);
    }
}
