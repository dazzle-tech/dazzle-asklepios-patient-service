package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.NephrologyRenalFunction;
import com.dazzle.asklepios.repository.NephrologyRenalFunctionRepository;
import com.dazzle.asklepios.service.dto.NephrologyRenalFunctionCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyRenalFunctionUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyRenalFunctionResponseVM;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class NephrologyRenalFunctionService {

    private final NephrologyRenalFunctionRepository repository;

    public NephrologyRenalFunctionResponseVM create(
            NephrologyRenalFunctionCreateDTO dto
    ) {
        repository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
                        dto.getPatientId(),
                        dto.getEncounterId()
                )
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Active nephrology renal function already exists for this patient and encounter"
                    );
                });

        NephrologyRenalFunction entity =
                NephrologyRenalFunction.builder()
                        .patientId(dto.getPatientId())
                        .encounterId(dto.getEncounterId())
                        .egfr(dto.getEgfr())
                        .creatinine(dto.getCreatinine())
                        .bun(dto.getBun())
                        .hemoglobin(dto.getHemoglobin())
                        .potassium(dto.getPotassium())
                        .sodium(dto.getSodium())
                        .calcium(dto.getCalcium())
                        .phosphorus(dto.getPhosphorus())
                        .isActive(true)
                        .build();

        return toResponseVM(repository.save(entity));
    }

    public NephrologyRenalFunctionResponseVM update(
            NephrologyRenalFunctionUpdateDTO dto
    ) {
        NephrologyRenalFunction entity = repository
                .findById(dto.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Nephrology renal function not found: " + dto.getId()
                        )
                );

        entity.setEgfr(dto.getEgfr());
        entity.setCreatinine(dto.getCreatinine());
        entity.setBun(dto.getBun());
        entity.setHemoglobin(dto.getHemoglobin());
        entity.setPotassium(dto.getPotassium());
        entity.setSodium(dto.getSodium());
        entity.setCalcium(dto.getCalcium());
        entity.setPhosphorus(dto.getPhosphorus());

        return toResponseVM(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<NephrologyRenalFunctionResponseVM>
    findByPatientAndEncounter(
            Long patientId,
            Long encounterId
    ) {

        return repository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
                        patientId,
                        encounterId
                )
                .map(this::toResponseVM);
    }

    private NephrologyRenalFunctionResponseVM toResponseVM(
            NephrologyRenalFunction entity
    ) {
        return NephrologyRenalFunctionResponseVM.builder()
                .id(entity.getId())
                .patientId(entity.getPatientId())
                .encounterId(entity.getEncounterId())
                .egfr(entity.getEgfr())
                .creatinine(entity.getCreatinine())
                .bun(entity.getBun())
                .hemoglobin(entity.getHemoglobin())
                .potassium(entity.getPotassium())
                .sodium(entity.getSodium())
                .calcium(entity.getCalcium())
                .phosphorus(entity.getPhosphorus())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedBy(entity.getLastModifiedBy())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }
}
