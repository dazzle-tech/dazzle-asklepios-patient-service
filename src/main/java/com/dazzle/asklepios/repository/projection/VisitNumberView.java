package com.dazzle.asklepios.repository.projection;

/**
 * Closed projection exposing only a row's visit number.
 */
public interface VisitNumberView {
    Integer getVisitNumber();
}
