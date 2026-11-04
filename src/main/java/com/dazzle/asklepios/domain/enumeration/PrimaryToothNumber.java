package com.dazzle.asklepios.domain.enumeration;

// Universal Numbering System for primary (deciduous/"baby") teeth: 20 teeth,
// lettered A-T. Separate from ToothNumber (permanent teeth, Tooth1-32, shared
// with the DentalProcedure feature) so that feature is never affected by
// pediatric chart support.
public enum PrimaryToothNumber {
    ToothA, ToothB, ToothC, ToothD, ToothE,
    ToothF, ToothG, ToothH, ToothI, ToothJ,
    ToothK, ToothL, ToothM, ToothN, ToothO,
    ToothP, ToothQ, ToothR, ToothS, ToothT
}
