package com.dazzle.asklepios.web.rest.vm.laboratory;
import com.dazzle.asklepios.domain.LabResultLog;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;

@Builder
public record LabResultLogResponseVM(
        Long id,
        Long resultId,
        Instant resultDate,
        String resultBy,
        String resultValue
) implements Serializable {

    public static LabResultLogResponseVM ofEntity(LabResultLog e) {
        return LabResultLogResponseVM.builder()
                .id(e.getId())
                .resultId(e.getResultId())
                .resultDate(e.getResultDate())
                .resultBy(e.getResultBy())
                .resultValue(e.getResultValue())
                .build();
    }
}
