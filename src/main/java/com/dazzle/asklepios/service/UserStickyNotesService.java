package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.UserStickyNotes;
import com.dazzle.asklepios.repository.UserStickyNotesRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserStickyNotesService {
    private static final Logger LOG = LoggerFactory.getLogger(UserStickyNotesService.class);

    private final UserStickyNotesRepository userStickyNotesRepository;

    public UserStickyNotesService(UserStickyNotesRepository userStickyNotesRepository) {
        this.userStickyNotesRepository = userStickyNotesRepository;
    }

    @Transactional(readOnly = true)
    public List<UserStickyNotes> findAllByUserId(long user_id) {
        LOG.debug("Fetching user sticky notes={}", user_id);
        return userStickyNotesRepository.findAllByUserIdOrderByPriorityOrderAsc(user_id);
    }

    public UserStickyNotes create(UserStickyNotes userStickyNotes) {
        LOG.info("[CREATE] Request to create UserStickyNotes for payload={}", userStickyNotes);

        UserStickyNotes entity = UserStickyNotes.builder()
                .userId(userStickyNotes.getUserId())
                .note(userStickyNotes.getNote())
                .priority(userStickyNotes.getPriority())
                .priorityOrder(userStickyNotes.getPriorityOrder())
                .color(userStickyNotes.getColor())
                .patientId(userStickyNotes.getPatientId())
                .build();

        try {
            UserStickyNotes saved = userStickyNotesRepository.saveAndFlush(entity);
            LOG.info("Successfully created User Sticky Note note={} ", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            throw new BadRequestAlertException(
                    "Database constraint violated while saving user Sticky Note (check unique fields or required values).",
                    "userStickyNote",
                    "db.constraint"
            );
        }
    }

    public boolean delete(Long id) {
        LOG.debug("Request to delete User Sticky Note : {}", id);
        if (!userStickyNotesRepository.existsById(id)) {
            return false;
        }

        userStickyNotesRepository.deleteById(id);
        return true;
    }

}
