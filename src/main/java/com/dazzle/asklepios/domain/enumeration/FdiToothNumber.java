package com.dazzle.asklepios.domain.enumeration;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FDI tooth codes used by dental procedures.
 * Permanent teeth are for patients older than 12. Primary teeth are for age 12 and under.
 * A missing date of birth is treated as permanent dentition.
 */
public enum FdiToothNumber {
    T18("18", Dentition.PERMANENT),
    T17("17", Dentition.PERMANENT),
    T16("16", Dentition.PERMANENT),
    T15("15", Dentition.PERMANENT),
    T14("14", Dentition.PERMANENT),
    T13("13", Dentition.PERMANENT),
    T12("12", Dentition.PERMANENT),
    T11("11", Dentition.PERMANENT),
    T21("21", Dentition.PERMANENT),
    T22("22", Dentition.PERMANENT),
    T23("23", Dentition.PERMANENT),
    T24("24", Dentition.PERMANENT),
    T25("25", Dentition.PERMANENT),
    T26("26", Dentition.PERMANENT),
    T27("27", Dentition.PERMANENT),
    T28("28", Dentition.PERMANENT),
    T48("48", Dentition.PERMANENT),
    T47("47", Dentition.PERMANENT),
    T46("46", Dentition.PERMANENT),
    T45("45", Dentition.PERMANENT),
    T44("44", Dentition.PERMANENT),
    T43("43", Dentition.PERMANENT),
    T42("42", Dentition.PERMANENT),
    T41("41", Dentition.PERMANENT),
    T31("31", Dentition.PERMANENT),
    T32("32", Dentition.PERMANENT),
    T33("33", Dentition.PERMANENT),
    T34("34", Dentition.PERMANENT),
    T35("35", Dentition.PERMANENT),
    T36("36", Dentition.PERMANENT),
    T37("37", Dentition.PERMANENT),
    T38("38", Dentition.PERMANENT),
    T55("55", Dentition.PRIMARY),
    T54("54", Dentition.PRIMARY),
    T53("53", Dentition.PRIMARY),
    T52("52", Dentition.PRIMARY),
    T51("51", Dentition.PRIMARY),
    T61("61", Dentition.PRIMARY),
    T62("62", Dentition.PRIMARY),
    T63("63", Dentition.PRIMARY),
    T64("64", Dentition.PRIMARY),
    T65("65", Dentition.PRIMARY),
    T85("85", Dentition.PRIMARY),
    T84("84", Dentition.PRIMARY),
    T83("83", Dentition.PRIMARY),
    T82("82", Dentition.PRIMARY),
    T81("81", Dentition.PRIMARY),
    T71("71", Dentition.PRIMARY),
    T72("72", Dentition.PRIMARY),
    T73("73", Dentition.PRIMARY),
    T74("74", Dentition.PRIMARY),
    T75("75", Dentition.PRIMARY);

    public enum Dentition {
        PERMANENT,
        PRIMARY
    }

    private static final Map<String, FdiToothNumber> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(FdiToothNumber::code, Function.identity()));

    private final String code;
    private final Dentition dentition;

    FdiToothNumber(String code, Dentition dentition) {
        this.code = code;
        this.dentition = dentition;
    }

    public String code() {
        return code;
    }

    public Dentition dentition() {
        return dentition;
    }

    public static FdiToothNumber fromCode(String value) {
        if (value == null) {
            return null;
        }
        return BY_CODE.get(value.trim());
    }

    public static Dentition dentitionFor(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return Dentition.PERMANENT;
        }
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        return age > 12 ? Dentition.PERMANENT : Dentition.PRIMARY;
    }
}
