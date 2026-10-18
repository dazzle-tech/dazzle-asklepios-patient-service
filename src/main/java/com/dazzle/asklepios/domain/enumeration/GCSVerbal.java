package com.dazzle.asklepios.domain.enumeration;

import lombok.Getter;

@Getter
public enum GCSVerbal {
    ORIENTED(5),
    CONFUSED_CONVERSATION(4),
    INAPPROPRIATE_WORDS(3),
    INCOMPREHENSIBLE_SOUNDS(2),
    NO_RESPONSE(1),
    INTUBATED_TRACHEOSTOMY(0, true);


    private final int score;

    /**
     * Retired values are kept only so existing assessments can still be read and edited;
     * they cannot be chosen for new assessments (hidden in the UI, rejected by the service).
     */
    private final boolean retired;

    GCSVerbal(int score) {
        this(score, false);
    }

    GCSVerbal(int score, boolean retired) {
        this.score = score;
        this.retired = retired;
    }
}