package com.dazzle.asklepios.service.dto.consultation;

import java.io.Serializable;

public record ConsultationSubmitErrorDTO(

        Long consultationId,
        Long consultationNumber,
        String message

) implements Serializable {
}
