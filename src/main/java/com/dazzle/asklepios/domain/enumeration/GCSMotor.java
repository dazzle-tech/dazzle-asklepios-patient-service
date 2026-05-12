package com.dazzle.asklepios.domain.enumeration;

import lombok.Getter;

@Getter
public enum GCSMotor {

    OBEYS_COMMANDS(6),
    LOCALIZES_PAIN(5),
    WITHDRAWS_FROM_PAIN(4),
    FLEXION_TO_PAIN(3),
    EXTENSION_TO_PAIN(2),
    NO_RESPONSE(1);

    private final int score;

    GCSMotor(int score) {
        this.score = score;
    }
}