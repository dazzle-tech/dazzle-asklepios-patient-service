package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothService;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the tooth and surface of a dental line for a Waseel dental (oral) pre-authorization:
 * a dental procedure keeps them on the procedure, a dental chart service on its tooth and action.
 */
@Component
@RequiredArgsConstructor
public class DentalSiteResolver {

    /** Value list of the tooth surfaces stored on a dental procedure (M, O, D, B, L, MOD, ...). */
    static final String TOOTH_SURFACE_LOV = "TOOTH_SURF";

    private static final Set<ServiceSource> DENTAL_CHART_SOURCES = EnumSet.of(
            ServiceSource.DENTAL_CHART_CDT_TREATMENT,
            ServiceSource.DENTAL_CHART_MANUAL_SERVICE
    );

    private final DentalProcedureRepository dentalProcedureRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final ToothActionRepository toothActionRepository;
    private final ApLovMapperService apLovMapperService;

    public static boolean isDentalTreatment(PatientServiceAndProduct item) {
        return item != null
                && (item.getServiceSource() == ServiceSource.DENTAL_PROCEDURE
                || DENTAL_CHART_SOURCES.contains(item.getServiceSource()));
    }

    public DentalSite resolve(PatientServiceAndProduct item) {
        if (!isDentalTreatment(item) || item.getSourceId() == null) {
            return DentalSite.NONE;
        }
        return item.getServiceSource() == ServiceSource.DENTAL_PROCEDURE
                ? fromDentalProcedure(item)
                : fromDentalChart(item.getSourceId());
    }

    /**
     * A dental procedure is billed one line per tooth, and the line keeps its tooth; a line from before
     * that change falls back to the procedure teeth. The surface is the procedure's.
     */
    private DentalSite fromDentalProcedure(PatientServiceAndProduct item) {
        return dentalProcedureRepository.findById(item.getSourceId())
                .map(procedure -> DentalSite.of(
                        item.getToothNumber() != null ? List.of(item.getToothNumber()) : procedure.getToothNumbers(),
                        apLovMapperService.getValueCodeByLovCodeAndKey(TOOTH_SURFACE_LOV, procedure.getSurface())
                ))
                .orElse(DentalSite.NONE);
    }

    private DentalSite fromDentalChart(Long toothServiceId) {
        ToothService toothService = toothServiceRepository.findById(toothServiceId).orElse(null);
        if (toothService == null) {
            return DentalSite.NONE;
        }
        String toothNumber = dentalChartToothRepository.findById(toothService.getToothId())
                .map(DentalChartTooth::getToothNumber)
                .orElse(null);
        ToothAction action = toothService.getToothActionId() == null
                ? null
                : toothActionRepository.findById(toothService.getToothActionId()).orElse(null);
        return DentalSite.of(toothNumber, action == null ? null : action.getSurface());
    }
}
