package com.dazzle.asklepios.web.rest.vm;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientComplaintAttachmentVM {

    private Long id;
    private String filename;
    private String mimeType;
    private Long sizeBytes;
    private Instant createdDate;
}
