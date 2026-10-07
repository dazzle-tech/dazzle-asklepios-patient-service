package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DialysisMedication;
import com.dazzle.asklepios.repository.DialysisMedicationRepository;
import com.dazzle.asklepios.repository.DialysisSessionRepository;
import com.dazzle.asklepios.service.dto.DialysisMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisMedicationUpdateDTO;
import com.dazzle.asklepios.service.helper.ActiveIngredientHelper;
import com.dazzle.asklepios.service.vm.DialysisMedicationResponseVM;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class DialysisMedicationService {

    private final DialysisMedicationRepository repository;
    private final DialysisSessionRepository dialysisSessionRepository;
    private final ActiveIngredientHelper activeIngredientHelper;

    public DialysisMedicationResponseVM create(
            DialysisMedicationCreateDTO dto
    ) {
        requireSession(dto.getDialysisSessionId());
        activeIngredientHelper.validateActiveIngredientExists(
                dto.getActiveIngredientId()
        );

        DialysisMedication entity =
                DialysisMedication.builder()
                        .dialysisSessionId(dto.getDialysisSessionId())
                        .activeIngredientId(dto.getActiveIngredientId())
                        .dose(dto.getDose())
                        .doseUnit(trimDoseUnit(dto.getDoseUnit()))
                        .build();

        return toResponseVM(repository.save(entity));
    }

    public DialysisMedicationResponseVM update(
            DialysisMedicationUpdateDTO dto
    ) {
        DialysisMedication entity = repository
                .findById(dto.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Dialysis medication not found: " + dto.getId()
                        )
                );

        requireSession(dto.getDialysisSessionId());
        activeIngredientHelper.validateActiveIngredientExists(
                dto.getActiveIngredientId()
        );

        entity.setDialysisSessionId(dto.getDialysisSessionId());
        entity.setActiveIngredientId(dto.getActiveIngredientId());
        entity.setDose(dto.getDose());
        entity.setDoseUnit(trimDoseUnit(dto.getDoseUnit()));

        return toResponseVM(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DialysisMedicationResponseVM> findByDialysisSessionId(
            Long dialysisSessionId
    ) {
        return repository
                .findByDialysisSessionIdOrderByIdAsc(dialysisSessionId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Dialysis medication not found: " + id
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

    private String trimDoseUnit(String doseUnit) {
        if (doseUnit == null) {
            return null;
        }

        String trimmed = doseUnit.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DialysisMedicationResponseVM toResponseVM(
            DialysisMedication entity
    ) {
        return DialysisMedicationResponseVM.builder()
                .id(entity.getId())
                .dialysisSessionId(entity.getDialysisSessionId())
                .activeIngredientId(entity.getActiveIngredientId())
                .dose(entity.getDose())
                .doseUnit(entity.getDoseUnit())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedBy(entity.getLastModifiedBy())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }
}
