package com.dazzle.asklepios.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressLocation {

    private Country country;
    private District district;
    private Community community;
    private Area area;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Country {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class District {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Community {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Area {
        private Long id;
        private String name;
    }
}

