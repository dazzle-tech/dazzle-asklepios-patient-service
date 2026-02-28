package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_sticky_notes")
public class UserStickyNotes extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Long userId;

    @Column(name = "note")
    @NotBlank
    private String note;


    @Column(name = "color", length = 30)
    @NotBlank
    private String color;

    @Column(name = "priority")
    @NotBlank
    private String priority;

    @Column(name = "priority_order", precision = 19, scale = 6)
    @NotNull
    private BigDecimal priorityOrder;

    @Column(name = "patient_id")
    private Long patientId;

}
