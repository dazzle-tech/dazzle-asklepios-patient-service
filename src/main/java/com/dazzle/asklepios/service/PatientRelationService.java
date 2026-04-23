package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.RelationsMatrix;
import com.dazzle.asklepios.domain.enumeration.FamilyMemberCategory;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.repository.PatientRelationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.RelationsMatrixRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Transactional
public class PatientRelationService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientRelationService.class);

    private final PatientRelationRepository relationRepository;
    private final RelationsMatrixRepository matrixRepository;
    private final PatientRepository patientRepository;

    public PatientRelationService(
            PatientRelationRepository relationRepository,
            RelationsMatrixRepository matrixRepository,
            PatientRepository patientRepository
    ) {
        this.relationRepository = relationRepository;
        this.matrixRepository = matrixRepository;
        this.patientRepository = patientRepository;
    }

    // --------------------------------------------------
    // CREATE with inverse
    // --------------------------------------------------
    public PatientRelation create(PatientRelation entity) {

        Long sourcePatientId = entity.getPatient().getId();
        Long relatedPatientId = entity.getRelativePatient().getId();
        RelationType requestedRelationType = entity.getRelationType();

        LOG.info("[CREATE] PatientRelation request patientId={} relativeId={} relationType={}", sourcePatientId, relatedPatientId, requestedRelationType);
        LOG.debug("Create PatientRelation request: patientId={}, relativeId={}, relationType={}", sourcePatientId, relatedPatientId, requestedRelationType);

        if (sourcePatientId.equals(relatedPatientId)) {
            LOG.warn("Reject create PatientRelation: same patientId={}", sourcePatientId);
            throw new BadRequestAlertException(
                    "samePatient",
                    "patientRelation",
                    "A patient2 cannot be linked to themselves."
            );
        }

        if (relationRepository.existsByPatient_IdAndRelativePatient_IdAndRelationType(sourcePatientId, relatedPatientId, requestedRelationType)) {
            LOG.warn("Reject create PatientRelation: duplicate relation patientId={}, relativeId={}, type={}", sourcePatientId, relatedPatientId, requestedRelationType);
            throw new BadRequestAlertException(
                    "Duplicate ",
                    "patientRelation",
                    "Duplicate relation is not allowed for the same patient2."
            );
        }
        if (requestedRelationType == RelationType.MOTHER || requestedRelationType == RelationType.FATHER) {
            boolean parentExists = relationRepository
                    .existsByPatient_IdAndRelationType(sourcePatientId, requestedRelationType);

            if (parentExists) {
                LOG.warn("Reject create PatientRelation: parent duplicate patientId={}, relationType={}", sourcePatientId, requestedRelationType);
                throw new BadRequestAlertException(
                        "parentDuplicate",
                        "patientRelation",
                        "Parent relation already exists."
                );
            }
        }
        // load genders from DB (important)
        Patient sourcePatient = patientRepository.findById(sourcePatientId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "PatientNotFound.",
                        "patientRelation",
                        "Patient not found."
                ));

        Patient relatedPatient = patientRepository.findById(relatedPatientId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "relativeNotFound ",
                        "patientRelation",
                        "Relative patient2 not found."
                ));

        entity.setPatient(sourcePatient);
        entity.setRelativePatient(relatedPatient);

        PatientRelation savedRelation = relationRepository.save(entity);
        LOG.debug("Saved PatientRelation id={} for patientId={}, relativeId={}", savedRelation.getId(), sourcePatientId, relatedPatientId);

        Gender sourcePatientGender = sourcePatient.getSexAtBirth();
        Gender relatedPatientGender = relatedPatient.getSexAtBirth();

        RelationsMatrix inverseMapping = matrixRepository
                .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(
                        sourcePatientGender,
                        relatedPatientGender,
                        requestedRelationType
                )
                .orElseThrow(() -> {
                    LOG.warn("No inverse mapping found in relations_matrix for patient1SexAtBirth={}, patient2SexAtBirth={}, relationType={}", sourcePatientGender, relatedPatientGender, requestedRelationType);
                    return new BadRequestAlertException(
                            "noInverse",
                            "patientRelation",
                            "Incorrect Relation, Gender is not match"
                    );
                });

        RelationType inverseRelationType = inverseMapping.getSecondRelationCode();

        boolean inverseExists =
                relationRepository.existsByPatient_IdAndRelativePatient_IdAndRelationType(relatedPatientId, sourcePatientId, inverseRelationType);

        if (!inverseExists) {
            PatientRelation inverseRelation = PatientRelation.builder()
                    .patient(relatedPatient)
                    .relativePatient(sourcePatient)
                    .relationType(inverseRelationType)
                    .categoryType(entity.getCategoryType())
                    .build();

            PatientRelation inverseSaved = relationRepository.save(inverseRelation);
            LOG.debug("Created inverse PatientRelation id={} for patientId={}, relativeId={}, type={}",
                    inverseSaved.getId(), relatedPatientId, sourcePatientId, inverseRelationType);
        } else {
            LOG.debug("Inverse relation already exists for patientId={}, relativeId={}, type={}", relatedPatientId, sourcePatientId, inverseRelationType);
        }

        LOG.info("[CREATE] PatientRelation success id={} patientId={} relativeId={} relationType={}",
                savedRelation.getId(), sourcePatientId, relatedPatientId, requestedRelationType);
        return savedRelation;
    }
   //-----------------------------------------------
   public PatientRelation update(Long id, PatientRelation updatedEntity) {
       LOG.info("[UPDATE] PatientRelation request id={} patientId={} relativeId={} oldRelationType={}",
               id, updatedEntity.getPatient().getId(), updatedEntity.getRelativePatient().getId(), updatedEntity.getRelationType());

       PatientRelation existingRelation = relationRepository.findById(id)
               .orElseThrow(() -> new BadRequestAlertException(
                       "notFound",
                       "patientRelation",
                       "Relation not found."
               ));

       Long existingSourcePatientId = existingRelation.getPatient().getId();
       Long existingRelatedPatientId = existingRelation.getRelativePatient().getId();
       RelationType existingRelationType = existingRelation.getRelationType();

       Patient newSourcePatient = patientRepository.findById(updatedEntity.getPatient().getId())
               .orElseThrow(() -> new BadRequestAlertException("patientNotFound","patientRelation","Patient not found."));
       Patient newRelatedPatient = patientRepository.findById(updatedEntity.getRelativePatient().getId())
               .orElseThrow(() -> new BadRequestAlertException("relativeNotFound","patientRelation","Relative patient not found."));

       RelationType newRelationType = updatedEntity.getRelationType();

       boolean relationChanged =
               !newSourcePatient.getId().equals(existingSourcePatientId) ||
                       !newRelatedPatient.getId().equals(existingRelatedPatientId) ||
                       newRelationType != existingRelationType;

       if (relationChanged) {

           Long newSourcePatientId = newSourcePatient.getId();

           if (newRelationType == RelationType.MOTHER || newRelationType == RelationType.FATHER) {

               boolean parentExists = relationRepository
                       .existsByPatient_IdAndRelationType(newSourcePatientId, newRelationType);


               boolean sameCurrentParent =
                       newSourcePatientId.equals(existingSourcePatientId) && newRelationType == existingRelationType;

               if (parentExists && !sameCurrentParent) {
                   LOG.warn("Reject update PatientRelation: parent duplicate patientId={} oldRelationType={} relationId={}",
                           newSourcePatientId, newRelationType, id);
                   throw new BadRequestAlertException(
                           "Parent relation already exists.",
                           "patientRelation",
                           "parentDuplicate"
                   );
               }
           }

           LOG.info("[UPDATE] Relationship changed id={} oldPatient1Id={} oldRelativeId={} oldRelationType={} newPatientId={} newRelativeId={} newRelationType={}",
                   id, existingSourcePatientId, existingRelatedPatientId, existingRelationType, newSourcePatient.getId(), newRelatedPatient.getId(), newRelationType);
           delete(id);
           return create(updatedEntity);
       }

       existingRelation.setCategoryType(updatedEntity.getCategoryType());

       PatientRelation savedRelation = relationRepository.save(existingRelation);

       Gender sourcePatientGender = newSourcePatient.getSexAtBirth();
       Gender relatedPatientGender = newRelatedPatient.getSexAtBirth();

       RelationType inverseRelationType = matrixRepository
               .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(sourcePatientGender, relatedPatientGender, existingRelationType)
               .map(RelationsMatrix::getSecondRelationCode)
               .orElse(null);

       if (inverseRelationType != null) {
           relationRepository
                   .findByPatient_IdAndRelativePatient_IdAndRelationType(existingRelatedPatientId, existingSourcePatientId, inverseRelationType)
                   .ifPresent(inverseRelation -> {
                       inverseRelation.setCategoryType(updatedEntity.getCategoryType());
                       relationRepository.save(inverseRelation);
                       LOG.debug("Updated inverse PatientRelation id={} for relationId={}", inverseRelation.getId(), id);
                   });
       }

       LOG.info("[UPDATE] PatientRelation success id={} patientId={} relativeId={} oldRelationType={}",
               savedRelation.getId(), existingSourcePatientId, existingRelatedPatientId, existingRelationType);
       return savedRelation;
   }

    // --------------------------------------------------
    public Page<PatientRelation> findAll(Pageable pageable) {
        LOG.debug("Find all PatientRelations page={}", pageable);
        return relationRepository.findAll(pageable);
    }

    public Page<PatientRelation> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("Find PatientRelations by patientId={} page={}", patientId, pageable);
        return relationRepository.findByPatient_Id(patientId, pageable);
    }

    public Optional<PatientRelation> findOne(Long id) {
        LOG.debug("Find one PatientRelation id={}", id);
        return relationRepository.findById(id);
    }

    public Page<Patient> findRelativePatientsByPatientIdAndCategoryType(
            Long patientId,
            FamilyMemberCategory categoryType,
            Pageable pageable
    ) {
        LOG.debug("Find relative patients by patientId={} categoryType={} page={}", patientId, categoryType, pageable);

        return relationRepository
                .findByPatient_IdAndCategoryType(patientId, categoryType, pageable)
                .map(PatientRelation::getRelativePatient);
    }
    // --------------------------------------------------
    // DELETE both sides
    // --------------------------------------------------
    public void delete(Long id) {
        LOG.info("[DELETE] PatientRelation request id={}", id);
        LOG.debug("Delete PatientRelation id={}", id);

        PatientRelation relationToDelete = relationRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notFound ",
                        "patientRelation",
                        "Relation not found."
                ));

        Long sourcePatientId = relationToDelete.getPatient().getId();
        Long relatedPatientId = relationToDelete.getRelativePatient().getId();
        RelationType relationType = relationToDelete.getRelationType();

        Gender sourcePatientGender = relationToDelete.getPatient().getSexAtBirth();
        Gender relatedPatientGender = relationToDelete.getRelativePatient().getSexAtBirth();

        RelationType inverseRelationType = matrixRepository
                .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(
                        sourcePatientGender,
                        relatedPatientGender,
                        relationType
                )
                .map(RelationsMatrix::getSecondRelationCode)
                .orElse(null);

        relationRepository.delete(relationToDelete);
        LOG.debug("Deleted PatientRelation id={} (patientId={}, relativeId={}, type={})", id, sourcePatientId, relatedPatientId, relationType);
        LOG.info("[DELETE] PatientRelation deleted id={} patientId={} relativeId={} relationType={}",
                id, sourcePatientId, relatedPatientId, relationType);

        if (inverseRelationType != null) {
            relationRepository
                    .findByPatient_IdAndRelativePatient_IdAndRelationType(relatedPatientId, sourcePatientId, inverseRelationType)
                    .ifPresent(inverseRelation -> {
                        relationRepository.delete(inverseRelation);
                        LOG.debug("Deleted inverse PatientRelation id={} (patientId={}, relativeId={}, type={})",
                                inverseRelation.getId(), relatedPatientId, sourcePatientId, inverseRelationType);
                        LOG.info("[DELETE] Inverse PatientRelation deleted id={} patientId={} relativeId={} relationType={}",
                                inverseRelation.getId(), relatedPatientId, sourcePatientId, inverseRelationType);
                    });
        } else {
            LOG.debug("No inverse type found for deletion. Skip inverse delete.");
        }
    }
}
