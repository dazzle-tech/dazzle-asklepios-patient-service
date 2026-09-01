package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimSubType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.WaseelClaimType;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Component;

/**
 * Splits invoice lines into Waseel claim types:
 * dental procedures → Dental, medications → Pharmacy, everything else → Professional.
 */
@Component
public class WaseelClaimClassification {

    public WaseelClaimType classify(PatientServiceAndProduct product) {
        if (product == null) {
            return WaseelClaimType.PROFESSIONAL;
        }
        if (product.getBillingItemType() == BillingItemTypes.MEDICATION) {
            return WaseelClaimType.PHARMACY;
        }
        if (product.getServiceSource() == ServiceSource.DENTAL_PROCEDURE) {
            return WaseelClaimType.DENTAL;
        }
        return WaseelClaimType.PROFESSIONAL;
    }

    public boolean isClaimableForType(PatientServiceAndProduct product, WaseelClaimType claimType) {
        if (product == null || product.isUncoveredCashItem() || claimType == null) {
            return false;
        }
        return classify(product) == claimType;
    }

    public boolean matchesEncounter(
            EncounterType encounterType,
            WaseelClaimType claimType,
            WaseelClaimSubType subType
    ) {
        if (claimType == null || subType == null) {
            return false;
        }
        if (claimType != WaseelClaimType.PROFESSIONAL) {
            return subType == WaseelClaimSubType.OUTPATIENT;
        }
        if (subType == WaseelClaimSubType.EMERGENCY) {
            return encounterType == EncounterType.EMERGENCY;
        }
        return encounterType == EncounterType.CLINIC || encounterType == EncounterType.DAYCASE;
    }

    public WaseelClaimSubType resolveSubType(WaseelClaimType claimType, EncounterType encounterType) {
        if (claimType == WaseelClaimType.PROFESSIONAL && encounterType == EncounterType.EMERGENCY) {
            return WaseelClaimSubType.EMERGENCY;
        }
        return WaseelClaimSubType.OUTPATIENT;
    }

    public void assertTypeAndSubType(WaseelClaimType claimType, WaseelClaimSubType subType) {
        if (claimType == null) {
            throw new BadRequestAlertException(
                    "Claim type is required",
                    "claim",
                    "claim.type.required"
            );
        }
        if (subType == null) {
            throw new BadRequestAlertException(
                    "Claim sub type is required",
                    "claim",
                    "claim.subType.required"
            );
        }
        if (claimType != WaseelClaimType.PROFESSIONAL && subType != WaseelClaimSubType.OUTPATIENT) {
            throw new BadRequestAlertException(
                    claimType.name() + " claims only support Outpatient sub type",
                    "claim",
                    "claim.subType.invalid"
            );
        }
    }

    public void assertEncounterMatches(
            EncounterType encounterType,
            WaseelClaimType claimType,
            WaseelClaimSubType subType
    ) {
        assertTypeAndSubType(claimType, subType);
        if (!matchesEncounter(encounterType, claimType, subType)) {
            throw new BadRequestAlertException(
                    "Invoice encounter type does not match the selected claim type and sub type",
                    "claim",
                    "claim.encounterType.mismatch"
            );
        }
    }
}
