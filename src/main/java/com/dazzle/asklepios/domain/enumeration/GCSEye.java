package com.dazzle.asklepios.domain.enumeration;

import lombok.Getter;

@Getter
public enum GCSEye {

    SPONTANEOUS(4),
    TO_SPEECH(3),
    TO_PAIN(2),
    NO_RESPONSE(1),
    EYES_CLOSED_DUE_TO_SWELLING(0);

    private final int score;

    GCSEye(int score) {
        this.score = score;
    }
}