package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreAuthorizationTrackRepository extends JpaRepository<PreAuthorizationTrack, Long> {

    List<PreAuthorizationTrack> findByPreAuthorization_IdAndTrackTypeOrderByCreatedDateDesc(
            Long preAuthorizationId,
            String trackType
    );

    long countByPreAuthorization_IdAndTrackType(Long preAuthorizationId, String trackType);
}
