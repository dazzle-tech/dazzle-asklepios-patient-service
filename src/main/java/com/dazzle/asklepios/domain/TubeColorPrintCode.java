package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tube_color_print_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TubeColorPrintCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tube_color_key", length = 100)
    private String tubeColorKey;

    @Column(name = "code", length = 3)
    private String code;
}
