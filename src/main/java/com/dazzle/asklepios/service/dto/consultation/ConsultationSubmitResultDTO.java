package com.dazzle.asklepios.service.dto.consultation;

import java.io.Serializable;
import java.util.List;

public record ConsultationSubmitResultDTO(

        Integer submittedCount,
        List<ConsultationSubmitErrorDTO> errors

) implements Serializable {
}
