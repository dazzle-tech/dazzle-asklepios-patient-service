package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DocumentTargetType;
import com.dazzle.asklepios.domain.enumeration.DocumentTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "document_assignment")
@Getter
@Setter
public class DocumentAssignment extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_definition_id", nullable = false)
    private DocumentDefinition documentDefinition;


    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private DocumentTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private DocumentTriggerType triggerType;

    @Column(name = "required", nullable = false)
    private Boolean required = false;

    @Column(name = "blocking", nullable = false)
    private Boolean blocking = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}