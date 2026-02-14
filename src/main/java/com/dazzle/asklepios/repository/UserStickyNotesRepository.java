package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.UserStickyNotes;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStickyNotesRepository extends JpaRepository<UserStickyNotes, Long> {

    List<UserStickyNotes> findAllByUserIdOrderByPriorityOrderAsc(long userId);

}
