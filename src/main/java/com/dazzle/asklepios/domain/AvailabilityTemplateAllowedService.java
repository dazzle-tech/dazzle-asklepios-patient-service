package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "availability_template_allowed_services")
@Getter
@Setter
public class AvailabilityTemplateAllowedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_id",nullable = false)
    private AvailabilityTemplate template;

    @ManyToOne
    @JoinColumn(name = "template_interval_id")
    private AvailabilityTemplateInterval interval;

    @Enumerated
    @Column(name = "service")
    private EncounterReason service;
}