package com.dazzle.asklepios.service.dto.radiology;

import java.util.List;

public record PacsResponseDTO(
        List<PacsStudyDTO> message
) {
}
