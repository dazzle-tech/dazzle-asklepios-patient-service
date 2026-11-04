package com.dazzle.asklepios.service.dailybriefing;

/**
 * Practitioner name and specialty read from the setup service.
 * {@code found} is false when that read failed. A null specialty means the practitioner record has none.
 */
public record PractitionerLabel(boolean found, String name, String specialty) {
}
