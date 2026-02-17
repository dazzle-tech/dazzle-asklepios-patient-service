package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.RelationsMatrix;
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

        Long patientOneID = entity.getPatient().getId();
        Long patientTowId = entity.getRelativePatient().getId();
        RelationType relationType = entity.getRelationType();

        LOG.info("[CREATE] PatientRelation request patientId={} relativeId={} relationType={}", patientOneID, patientTowId, relationType);
        LOG.debug("Create PatientRelation request: patientId={}, relativeId={}, relationType={}", patientOneID, patientTowId, relationType);

        if (patientOneID.equals(patientTowId)) {
            LOG.warn("Reject create PatientRelation: same patientId={}", patientOneID);
            throw new BadRequestAlertException(
                    "A patient2 cannot be linked to themselves.",
                    "patientRelation",
                    "samePatient"
            );
        }

        if (relationRepository.existsByPatient_IdAndRelativePatient_IdAndRelationType(patientOneID, patientTowId, relationType)) {
            LOG.warn("Reject create PatientRelation: duplicate relation patientId={}, relativeId={}, type={}", patientOneID, patientTowId, relationType);
            throw new BadRequestAlertException(
                    "Duplicate ",
                    "patientRelation",
                    "Duplicate relation is not allowed for the same patient2."
            );
        }
        if (relationType == RelationType.MOTH || relationType == RelationType.FATH) {
            boolean parentExists = relationRepository
                    .existsByPatient_IdAndRelationType(patientOneID, relationType);

            if (parentExists) {
                LOG.warn("Reject create PatientRelation: parent duplicate patientId={}, relationType={}", patientOneID, relationType);
                throw new BadRequestAlertException(
                        "parentDuplicate",
                        "patientRelation",
                        "Parent relation already exists."
                );
            }
        }
        // load genders from DB (important)
        Patient patient1 = patientRepository.findById(patientOneID)
                .orElseThrow(() -> new BadRequestAlertException(
                        "PatientNotFound.",
                        "patientRelation",
                        "Patient not found."
                ));

        Patient patient2 = patientRepository.findById(patientTowId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "relativeNotFound ",
                        "patientRelation",
                        "Relative patient2 not found."
                ));

        entity.setPatient(patient1);
        entity.setRelativePatient(patient2);

        PatientRelation saved = relationRepository.save(entity);
        LOG.debug("Saved PatientRelation id={} for patientId={}, relativeId={}", saved.getId(), patientOneID, patientTowId);

        Gender patient1SexAtBirth = patient1.getSexAtBirth();
        Gender patient2SexAtBirth = patient2.getSexAtBirth();

        RelationsMatrix matrix = matrixRepository
                .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCodeWithLog(
                        patient1SexAtBirth,
                        patient2SexAtBirth,
                        relationType
                )
                .orElseThrow(() -> {
                    LOG.warn("No inverse mapping found in relations_matrix for patient1SexAtBirth={}, patient2SexAtBirth={}, relationType={}", patient1SexAtBirth, patient2SexAtBirth, relationType);
                    return new BadRequestAlertException(
                            "noInverse",
                            "patientRelation",
                            "Incorrect Relation, Gender is not match"
                    );
                });

        RelationType inverseType = matrix.getSecondRelationCode();

        boolean inverseExists =
                relationRepository.existsByPatient_IdAndRelativePatient_IdAndRelationType(patientTowId, patientOneID, inverseType);

        if (!inverseExists) {
            PatientRelation inverse = PatientRelation.builder()
                    .patient(patient2)
                    .relativePatient(patient1)
                    .relationType(inverseType)
                    .categoryType(entity.getCategoryType())
                    .isActive(true)
                    .build();

            PatientRelation inverseSaved = relationRepository.save(inverse);
            LOG.debug("Created inverse PatientRelation id={} for patientId={}, relativeId={}, type={}",
                    inverseSaved.getId(), patientTowId, patientOneID, inverseType);
        } else {
            LOG.debug("Inverse relation already exists for patientId={}, relativeId={}, type={}", patientTowId, patientOneID, inverseType);
        }

        LOG.info("[CREATE] PatientRelation success id={} patientId={} relativeId={} relationType={}",
                saved.getId(), patientOneID, patientTowId, relationType);
        return saved;
    }
   //-----------------------------------------------
   public PatientRelation update(Long id, PatientRelation newEntity) {
       LOG.info("[UPDATE] PatientRelation request id={} patientId={} relativeId={} relationType={}",
               id, newEntity.getPatient().getId(), newEntity.getRelativePatient().getId(), newEntity.getRelationType());

       PatientRelation old = relationRepository.findById(id)
               .orElseThrow(() -> new BadRequestAlertException(
                       "notFound",
                       "patientRelation",
                       "Relation not found."
               ));

       Long oldP1Id = old.getPatient().getId();
       Long oldP2Id = old.getRelativePatient().getId();
       RelationType oldType = old.getRelationType();

       Patient p1 = patientRepository.findById(newEntity.getPatient().getId())
               .orElseThrow(() -> new BadRequestAlertException("patientNotFound","patientRelation","Patient not found."));
       Patient p2 = patientRepository.findById(newEntity.getRelativePatient().getId())
               .orElseThrow(() -> new BadRequestAlertException("relativeNotFound","patientRelation","Relative patient not found."));

       RelationType newType = newEntity.getRelationType();

       boolean relationChanged =
               !p1.getId().equals(oldP1Id) ||
                       !p2.getId().equals(oldP2Id) ||
                       newType != oldType;

       if (relationChanged) {

           Long newP1Id = p1.getId();

           if (newType == RelationType.MOTH || newType == RelationType.FATH) {

               boolean parentExists = relationRepository
                       .existsByPatient_IdAndRelationType(newP1Id, newType);


               boolean sameCurrentParent =
                       newP1Id.equals(oldP1Id) && newType == oldType;

               if (parentExists && !sameCurrentParent) {
                   LOG.warn("Reject update PatientRelation: parent duplicate patientId={} relationType={} relationId={}",
                           newP1Id, newType, id);
                   throw new BadRequestAlertException(
                           "Parent relation already exists.",
                           "patientRelation",
                           "parentDuplicate"
                   );
               }
           }

           LOG.info("[UPDATE] Relationship changed id={} oldPatientId={} oldRelativeId={} oldType={} newPatientId={} newRelativeId={} newType={}",
                   id, oldP1Id, oldP2Id, oldType, p1.getId(), p2.getId(), newType);
           delete(id);
           return create(newEntity);
       }

       old.setCategoryType(newEntity.getCategoryType());
       old.setIsActive(newEntity.getIsActive());

       PatientRelation savedOld = relationRepository.save(old);

       Gender g1 = p1.getSexAtBirth();
       Gender g2 = p2.getSexAtBirth();

       RelationType inverseType = matrixRepository
               .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCodeWithLog(g1, g2, oldType)
               .map(RelationsMatrix::getSecondRelationCode)
               .orElse(null);

       if (inverseType != null) {
           relationRepository
                   .findByPatient_IdAndRelativePatient_IdAndRelationType(oldP2Id, oldP1Id, inverseType)
                   .ifPresent(inv -> {
                       inv.setCategoryType(newEntity.getCategoryType());
                       inv.setIsActive(newEntity.getIsActive());
                       relationRepository.save(inv);
                       LOG.debug("Updated inverse PatientRelation id={} for relationId={}", inv.getId(), id);
                   });
       }

       LOG.info("[UPDATE] PatientRelation success id={} patientId={} relativeId={} relationType={}",
               savedOld.getId(), oldP1Id, oldP2Id, oldType);
       return savedOld;
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

    // --------------------------------------------------
    // DELETE both sides
    // --------------------------------------------------
    public void delete(Long id) {
        LOG.info("[DELETE] PatientRelation request id={}", id);
        LOG.debug("Delete PatientRelation id={}", id);

        PatientRelation patientRelation = relationRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notFound ",
                        "patientRelation",
                        "Relation not found."
                ));

        Long firstPatientId = patientRelation.getPatient().getId();
        Long secondPatientId = patientRelation.getRelativePatient().getId();
        RelationType relationType = patientRelation.getRelationType();

        Gender firstPatientGender = patientRelation.getPatient().getSexAtBirth();
        Gender secondPatientGender = patientRelation.getRelativePatient().getSexAtBirth();

        RelationType inverseType = matrixRepository
                .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCodeWithLog(
                        firstPatientGender,
                        secondPatientGender,
                        relationType
                )
                .map(RelationsMatrix::getSecondRelationCode)
                .orElse(null);

        relationRepository.delete(patientRelation);
        LOG.debug("Deleted PatientRelation id={} (patientId={}, relativeId={}, type={})", id, firstPatientId, secondPatientId, relationType);
        LOG.info("[DELETE] PatientRelation deleted id={} patientId={} relativeId={} relationType={}",
                id, firstPatientId, secondPatientId, relationType);

        if (inverseType != null) {
            relationRepository
                    .findByPatient_IdAndRelativePatient_IdAndRelationType(secondPatientId, firstPatientId, inverseType)
                    .ifPresent(inv -> {
                        relationRepository.delete(inv);
                        LOG.debug("Deleted inverse PatientRelation id={} (patientId={}, relativeId={}, type={})",
                                inv.getId(), secondPatientId, firstPatientId, inverseType);
                        LOG.info("[DELETE] Inverse PatientRelation deleted id={} patientId={} relativeId={} relationType={}",
                                inv.getId(), secondPatientId, firstPatientId, inverseType);
                    });
        } else {
            LOG.debug("No inverse type found for deletion. Skip inverse delete.");
        }
    }
}
