package com.dazzle.asklepios.service.dto.dentalChart;

import java.io.Serializable;

public record ProgressNoteSaveDTO(
        Long id,
        String note
) implements Serializable {}
