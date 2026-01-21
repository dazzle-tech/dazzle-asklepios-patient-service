
package com.dazzle.asklepios.client.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "diagnostic_test_radiology")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticTestRadiology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false, unique = true)
    private DiagnosticTest test;

    @Column(name = "category")
    private String category;
}
