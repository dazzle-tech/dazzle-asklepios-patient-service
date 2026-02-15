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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "lab_result_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResultLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "result_id", nullable = false)
    private Long resultId;

    @NotNull
    @Column(name = "result_date", nullable = false)
    private Instant resultDate;

    @NotNull
    @Column(name = "result_by", length = 50)
    private String resultBy;


    @Column(name = "result_value", nullable = false)
    private String resultValue;
}
