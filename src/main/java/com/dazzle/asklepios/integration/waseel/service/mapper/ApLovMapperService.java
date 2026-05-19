package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApLovMapperService {

    private final ApLovValueRepository apLovValueRepository;

    public String getKeyByLovCodeAndValueCode(String lovCode, String valueCode) {
        if (isBlank(lovCode) || isBlank(valueCode)) {
            return "";
        }

        List<ApLovValue> values = apLovValueRepository.findByLovCodeAndIsValidTrue(lovCode);
        String normalizedValueCode = normalize(valueCode);

        return values.stream()
                .filter(v -> normalize(v.getValueCode()).equals(normalizedValueCode))
                .map(ApLovValue::getKey)
                .findFirst()
                .orElse("");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}