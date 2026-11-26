package com.dazzle.asklepios.web.rest.vm;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientComplaintSubmitResponseVM {

    private String reference;
    private Instant receivedDate;
}
