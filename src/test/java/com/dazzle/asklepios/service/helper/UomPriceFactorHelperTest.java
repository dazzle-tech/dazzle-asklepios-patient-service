package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.dto.UomGroupRelationDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UomPriceFactorHelperTest {

    private static final Long GROUP_ID = 5L;
    private static final Long BOX = 1L;
    private static final Long STRIP = 2L;
    private static final Long TABLET = 3L;
    private static final Long VIAL = 4L;

    // 1 BOX = 3 STRIP, 1 STRIP = 10 TABLET
    private final UomPriceFactorHelper helper = new UomPriceFactorHelper(groupId -> List.of(
            relation(BOX, STRIP, "3"),
            relation(STRIP, TABLET, "10")
    ), null);

    @Test
    void sameUnitAsPriceBaseHasFactorOne() {
        assertEquals(BigDecimal.ONE, helper.resolveFactor(GROUP_ID, TABLET, TABLET));
    }

    @Test
    void biggerUnitIsPricedAsManyBaseUnits() {
        // Price per TABLET, charged per STRIP: 10 tablets
        assertEquals(0, new BigDecimal("10").compareTo(helper.resolveFactor(GROUP_ID, STRIP, TABLET)));
    }

    @Test
    void relationsAreChained() {
        // Price per TABLET, charged per BOX: 3 × 10 tablets
        assertEquals(0, new BigDecimal("30").compareTo(helper.resolveFactor(GROUP_ID, BOX, TABLET)));
    }

    @Test
    void relationsAreUsedInReverse() {
        // Price per BOX, charged per STRIP: a third of a box
        BigDecimal factor = helper.resolveFactor(GROUP_ID, STRIP, BOX);

        assertEquals(0, new BigDecimal("90").compareTo(new BigDecimal("270").multiply(factor).setScale(4, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void unitWithoutRelationIsRejected() {
        assertThrows(BadRequestAlertException.class, () -> helper.resolveFactor(GROUP_ID, VIAL, TABLET));
    }

    private static UomGroupRelationDTO relation(Long from, Long to, String value) {
        return new UomGroupRelationDTO(
                null,
                new BigDecimal(value),
                new UomGroupRelationDTO.Unit(from, null),
                new UomGroupRelationDTO.Unit(to, null)
        );
    }
}
