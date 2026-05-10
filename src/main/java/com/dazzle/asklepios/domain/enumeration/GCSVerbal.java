package com.dazzle.asklepios.domain.enumeration;

import lombok.Getter;

@Getter
public enum GCSVerbal {
    ORIENTED(5),
    CONFUSED_CONVERSATION(4),
    INAPPROPRIATE_WORDS(3),
    INCOMPREHENSIBLE_SOUNDS(2),
    NO_RESPONSE(1),
    INTUBATED_TRACHEOSTOMY(0);

    private final int score;

    GCSVerbal(int score) {
        this.score = score;
    }
}