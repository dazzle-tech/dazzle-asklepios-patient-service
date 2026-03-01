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

    public static LabResultLogResponseVM ofEntity(LabResultLog labResultLog) {
        return LabResultLogResponseVM.builder()
                .id(labResultLog.getId())
                .resultId(labResultLog.getResultId())
                .resultDate(labResultLog.getResultDate())
                .resultBy(labResultLog.getResultBy())
                .resultValue(labResultLog.getResultValue())
                .build();
    }
}
