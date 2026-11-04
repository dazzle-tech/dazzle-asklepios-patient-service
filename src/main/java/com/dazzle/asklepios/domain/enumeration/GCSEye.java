package com.dazzle.asklepios.domain.enumeration;

import lombok.Getter;

@Getter
public enum GCSEye {

    SPONTANEOUS(4),
    TO_SPEECH(3),
    TO_PAIN(2),
    NO_RESPONSE(1),
    EYES_CLOSED_DUE_TO_SWELLING(0, true);

    private final int score;

    /**
     * Retired values are kept only so existing assessments can still be read and edited;
     * they cannot be chosen for new assessments (hidden in the UI, rejected by the service).
     */
    private final boolean retired;

    GCSEye(int score) {
        this(score, false);
    }

    GCSEye(int score, boolean retired) {
        this.score = score;
        this.retired = retired;
    }
}