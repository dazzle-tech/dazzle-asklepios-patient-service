package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.UserStickyNotes;
import com.dazzle.asklepios.service.UserStickyNotesService;
import com.dazzle.asklepios.service.dto.UserStickyNotesCreateDTO;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
public class UserStickyNotesController {

    private static final Logger LOG = LoggerFactory.getLogger(UserStickyNotesController.class);

    private final UserStickyNotesService userStickyNotesService;

    public UserStickyNotesController(UserStickyNotesService userStickyNotesService) {
        this.userStickyNotesService = userStickyNotesService;
    }




    @GetMapping("/user-sticky-notes/{user_id}")
    public ResponseEntity<List<UserStickyNotes>> findAllByUserId(
            @PathVariable long user_id
    ) {
        LOG.debug("REST list User Sticky Notes user_id={}", user_id);

        final List<UserStickyNotes> list = userStickyNotesService.findAllByUserId(user_id);

        return ResponseEntity.ok(list);
    }

    @PostMapping("/user-sticky-notes")
    public ResponseEntity<UserStickyNotes> createUserStickyNotes(
            @Valid @RequestBody UserStickyNotesCreateDTO vm
    ) {
        LOG.debug("REST create User sticky Note payload={}", vm);

        UserStickyNotes toCreate = UserStickyNotes.builder()
                .note(vm.note())
                .priority(vm.priority())
                .color(vm.color())
                .userId(vm.userId())
                .priorityOrder(vm.priorityOrder())
                .patientId(vm.patientId() != null ? Long.parseLong(vm.patientId()) : null)
                .build();

        UserStickyNotes created = userStickyNotesService.create(toCreate);

        return ResponseEntity
                .created(URI.create("/api/setup/user-sticky-notes/" + created.getId()))
                .body(created);
    }

    @DeleteMapping("/user-sticky-notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST request to delete user sticky note id={}", id);
        boolean removed = userStickyNotesService.delete(id);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

}
