package com.dazzle.asklepios.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientActivityChecklistItem implements Serializable {

    private String label;

    private boolean done;
}
