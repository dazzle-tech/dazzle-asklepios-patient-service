package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.TestResultType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of the setup-owned {@code diagnostic_test_profile} table.
 */
@Entity
@Immutable
@Table(name = "diagnostic_test_profile")
@Getter
@NoArgsConstructor
public class DiagnosticTestProfile {

    @Id
    private Long id;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "name")
    private String name;

    @Column(name = "result_unit")
    private String resultUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type")
    private TestResultType resultType;
}
