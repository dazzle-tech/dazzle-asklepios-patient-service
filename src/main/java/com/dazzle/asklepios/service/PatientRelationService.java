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

        Long p1Id = entity.getPatient().getId();
        Long p2Id = entity.getRelativePatient().getId();
        RelationType r1 = entity.getRelationType();

        LOG.debug("Create PatientRelation request: patientId={}, relativeId={}, relationType={}", p1Id, p2Id, r1);

        if (p1Id.equals(p2Id)) {
            LOG.warn("Reject create PatientRelation: same patientId={}", p1Id);
            throw new BadRequestAlertException(
                    "A patient cannot be linked to themselves.",
                    "patientRelation",
                    "samePatient"
            );
        }

        if (relationRepository.existsByPatient_IdAndRelativePatient_IdAndRelationType(p1Id, p2Id, r1)) {
            LOG.warn("Reject create PatientRelation: duplicate relation patientId={}, relativeId={}, type={}", p1Id, p2Id, r1);
            throw new BadRequestAlertException(
                    "Duplicate ",
                    "patientRelation",
                    "Duplicate relation is not allowed for the same patient."
            );
        }
        if (r1 == RelationType.MOTH || r1 == RelationType.FATH) {
            boolean parentExists = relationRepository
                    .existsByPatient_IdAndRelationType(p1Id, r1);

            if (parentExists) {
                throw new BadRequestAlertException(
                        "parentDuplicate",
                        "patientRelation",
                        "Parent relation already exists."
                );
            }
        }
        // load genders from DB (important)
        Patient p1 = patientRepository.findById(p1Id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "PatientNotFound.",
                        "patientRelation",
                        "Patient not found."
                ));

        Patient p2 = patientRepository.findById(p2Id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "relativeNotFound ",
                        "patientRelation",
                        "Relative patient not found."
                ));

        entity.setPatient(p1);
        entity.setRelativePatient(p2);

        PatientRelation saved = relationRepository.save(entity);
        LOG.debug("Saved PatientRelation id={} for patientId={}, relativeId={}", saved.getId(), p1Id, p2Id);

        Gender g1 = p1.getSexAtBirth();
        Gender g2 = p2.getSexAtBirth();

        RelationsMatrix matrix = matrixRepository
                .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(g1, g2, r1)
                .orElseThrow(() -> {
                    LOG.warn("No inverse mapping found in relations_matrix for g1={}, g2={}, r1={}", g1, g2, r1);
                    return new BadRequestAlertException(
                            "noInverse",
                            "patientRelation",
                            "Incorrect Relation, Gender is not match"
                    );
                });

        RelationType inverseType = matrix.getSecondRelationCode();

        boolean inverseExists =
                relationRepository.existsByPatient_IdAndRelativePatient_IdAndRelationType(p2Id, p1Id, inverseType);

        if (!inverseExists) {
            PatientRelation inverse = PatientRelation.builder()
                    .patient(p2)
                    .relativePatient(p1)
                    .relationType(inverseType)
                    .categoryType(entity.getCategoryType())
                    .isActive(true)
                    .build();

            PatientRelation inverseSaved = relationRepository.save(inverse);
            LOG.debug("Created inverse PatientRelation id={} for patientId={}, relativeId={}, type={}",
                    inverseSaved.getId(), p2Id, p1Id, inverseType);
        } else {
            LOG.debug("Inverse relation already exists for patientId={}, relativeId={}, type={}", p2Id, p1Id, inverseType);
        }

        return saved;
    }
   //-----------------------------------------------
   public PatientRelation update(Long id, PatientRelation newEntity) {

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

           Long newP1Id = p1.getId(); // المريض بعد التعديل

           // ✅ فحص عدم تكرار الأم/الأب قبل الحذف
           if (newType == RelationType.MOTH || newType == RelationType.FATH) {

               boolean parentExists = relationRepository
                       .existsByPatient_IdAndRelationType(newP1Id, newType);

            /*
              إذا كان موجود Parent relation:
              - وهاي ليست نفس العلاقة الحالية (يعني مش مجرد تعديل على نفس الأم/الأب)
              إذن ارفض.
            */
               boolean sameCurrentParent =
                       newP1Id.equals(oldP1Id) && newType == oldType;

               if (parentExists && !sameCurrentParent) {
                   throw new BadRequestAlertException(
                           "Parent relation already exists.",
                           "patientRelation",
                           "parentDuplicate"
                   );
               }
           }

           // بعد ما اطمّنا، احذف القديم وأنشئ الجديد
           delete(id);
           return create(newEntity);
       }

       // metadata only -> update both sides
       old.setCategoryType(newEntity.getCategoryType());
       old.setIsActive(newEntity.getIsActive());

       PatientRelation savedOld = relationRepository.save(old);

       Gender g1 = p1.getSexAtBirth();
       Gender g2 = p2.getSexAtBirth();

       RelationType inverseType = matrixRepository
               .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(g1, g2, oldType)
               .map(RelationsMatrix::getSecondRelationCode)
               .orElse(null);

       if (inverseType != null) {
           relationRepository
                   .findByPatient_IdAndRelativePatient_IdAndRelationType(oldP2Id, oldP1Id, inverseType)
                   .ifPresent(inv -> {
                       inv.setCategoryType(newEntity.getCategoryType());
                       inv.setIsActive(newEntity.getIsActive());
                       relationRepository.save(inv);
                   });
       }

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
        LOG.debug("Delete PatientRelation id={}", id);

        PatientRelation rel = relationRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notFound ",
                        "patientRelation",
                        "Relation not found."
                ));

        Long p1 = rel.getPatient().getId();
        Long p2 = rel.getRelativePatient().getId();
        RelationType r1 = rel.getRelationType();

        Gender g1 = rel.getPatient().getSexAtBirth();
        Gender g2 = rel.getRelativePatient().getSexAtBirth();

        RelationType inverseType = matrixRepository
                .findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(g1, g2, r1)
                .map(RelationsMatrix::getSecondRelationCode)
                .orElse(null);

        relationRepository.delete(rel);
        LOG.debug("Deleted PatientRelation id={} (patientId={}, relativeId={}, type={})", id, p1, p2, r1);

        if (inverseType != null) {
            relationRepository
                    .findByPatient_IdAndRelativePatient_IdAndRelationType(p2, p1, inverseType)
                    .ifPresent(inv -> {
                        relationRepository.delete(inv);
                        LOG.debug("Deleted inverse PatientRelation id={} (patientId={}, relativeId={}, type={})",
                                inv.getId(), p2, p1, inverseType);
                    });
        } else {
            LOG.debug("No inverse type found for deletion. Skip inverse delete.");
        }
    }
}
