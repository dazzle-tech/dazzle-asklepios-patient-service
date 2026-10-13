package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "claim_encounter_progress_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimEncounterProgressNote
        extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "claim_encounter_copy_id", nullable = false)
    private Long claimEncounterCopyId;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "progress_note_id", nullable = false)
    private Long progressNoteId;

    @NotNull
    @Column(name = "note_text", nullable = false)
    private String noteText;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_date")
    private Instant cancelledDate;

    @Column(name = "cancellation_reason")
    private String cancellationReason;
}