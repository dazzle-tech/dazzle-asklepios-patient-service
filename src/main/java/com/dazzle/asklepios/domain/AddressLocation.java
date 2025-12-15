package com.dazzle.asklepios.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressLocation {

    private SimpleCountry country;
    private SimpleDistrict district;
    private SimpleCommunity community;
    private SimpleArea area;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleCountry {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleDistrict {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleCommunity {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleArea {
        private Long id;
        private String name;
    }
}

