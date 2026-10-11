package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DialysisFlowReading;
import com.dazzle.asklepios.repository.DialysisFlowReadingRepository;
import com.dazzle.asklepios.repository.DialysisSessionRepository;
import com.dazzle.asklepios.service.dto.DialysisFlowReadingCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisFlowReadingUpdateDTO;
import com.dazzle.asklepios.service.vm.DialysisFlowReadingResponseVM;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DialysisFlowReadingService {

    private final DialysisFlowReadingRepository repository;
    private final DialysisSessionRepository dialysisSessionRepository;

    public DialysisFlowReadingResponseVM create(
            DialysisFlowReadingCreateDTO dto
    ) {
        requireSession(dto.getDialysisSessionId());

        DialysisFlowReading entity =
                DialysisFlowReading.builder()
                        .dialysisSessionId(dto.getDialysisSessionId())
                        .time(dto.getTime())
                        .bloodPressureSystolic(dto.getBloodPressureSystolic())
                        .bloodPressureDiastolic(dto.getBloodPressureDiastolic())
                        .pulse(dto.getPulse())
                        .ufRate(dto.getUfRate())
                        .ufRemoved(dto.getUfRemoved())
                        .arterialPressure(dto.getArterialPressure())
                        .venousPressure(dto.getVenousPressure())
                        .transmembranePressure(dto.getTransmembranePressure())
                        .build();

        return toResponseVM(repository.save(entity));
    }

    public DialysisFlowReadingResponseVM update(
            DialysisFlowReadingUpdateDTO dto
    ) {
        DialysisFlowReading entity = repository
                .findById(dto.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Dialysis flow reading not found: " + dto.getId()
                        )
                );

        requireSession(dto.getDialysisSessionId());

        entity.setDialysisSessionId(dto.getDialysisSessionId());
        entity.setTime(dto.getTime());
        entity.setBloodPressureSystolic(dto.getBloodPressureSystolic());
        entity.setBloodPressureDiastolic(dto.getBloodPressureDiastolic());
        entity.setPulse(dto.getPulse());
        entity.setUfRate(dto.getUfRate());
        entity.setUfRemoved(dto.getUfRemoved());
        entity.setArterialPressure(dto.getArterialPressure());
        entity.setVenousPressure(dto.getVenousPressure());
        entity.setTransmembranePressure(dto.getTransmembranePressure());

        return toResponseVM(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DialysisFlowReadingResponseVM> findByDialysisSessionId(
            Long dialysisSessionId
    ) {
        return repository
                .findByDialysisSessionIdOrderByTimeAsc(dialysisSessionId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Dialysis flow reading not found: " + id
            );
        }

        repository.deleteById(id);
    }

    private void requireSession(Long dialysisSessionId) {
        if (!dialysisSessionRepository.existsById(dialysisSessionId)) {
            throw new EntityNotFoundException(
                    "Dialysis session not found: " + dialysisSessionId
            );
        }
    }

    private DialysisFlowReadingResponseVM toResponseVM(
            DialysisFlowReading entity
    ) {
        return DialysisFlowReadingResponseVM.builder()
                .id(entity.getId())
                .dialysisSessionId(entity.getDialysisSessionId())
                .time(entity.getTime())
                .bloodPressureSystolic(entity.getBloodPressureSystolic())
                .bloodPressureDiastolic(entity.getBloodPressureDiastolic())
                .pulse(entity.getPulse())
                .ufRate(entity.getUfRate())
                .ufRemoved(entity.getUfRemoved())
                .arterialPressure(entity.getArterialPressure())
                .venousPressure(entity.getVenousPressure())
                .transmembranePressure(entity.getTransmembranePressure())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedBy(entity.getLastModifiedBy())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }
}
