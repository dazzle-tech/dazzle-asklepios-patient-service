package com.dazzle.asklepios.integration.waseel.dto;

public record WaseelAuthRequest(
        String username,
        String password
) {}