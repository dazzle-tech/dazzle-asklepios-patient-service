package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationItemMatcher;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Maha's case (NPHIES BV-01265): a procedure on teeth 43 and 34 is billed one line per tooth,
 * and every line goes to Waseel with its own tooth as bodySite.
 */
class DentalSiteResolverTest {

    private static final String SURFACE_KEY = "2443644414056145";

    private DentalSiteResolver resolver;

    @BeforeEach
    void setUp() {
        DentalProcedure procedure = new DentalProcedure();
        procedure.setId(17L);
        procedure.setToothNumbers(List.of("43", "34"));
        procedure.setSurface(SURFACE_KEY);

        DentalProcedureRepository dentalProcedureRepository = mock(DentalProcedureRepository.class);
        when(dentalProcedureRepository.findById(17L)).thenReturn(Optional.of(procedure));
        ApLovMapperService apLovMapperService = mock(ApLovMapperService.class);
        when(apLovMapperService.getValueCodeByLovCodeAndKey(DentalSiteResolver.TOOTH_SURFACE_LOV, SURFACE_KEY)).thenReturn("D");

        resolver = new DentalSiteResolver(
                dentalProcedureRepository,
                mock(ToothServiceRepository.class),
                mock(DentalChartToothRepository.class),
                mock(ToothActionRepository.class),
                apLovMapperService
        );
    }

    @Test
    void eachToothLineIsSentWithItsTooth() {
        assertThat(resolver.resolve(toothLine(1512L, "43"))).isEqualTo(new DentalSite("43", "D"));
        assertThat(resolver.resolve(toothLine(1513L, "34"))).isEqualTo(new DentalSite("34", "D"));
    }

    @Test
    void aLineFromBeforeTheChangeCoveringTwoTeethHasNoTooth() {
        assertThat(resolver.resolve(toothLine(1499L, null))).isEqualTo(DentalSite.NONE);
    }

    @Test
    void theDecisionOfEachToothReachesItsOwnLine() {
        PreAuthorizationItem tooth43 = PreAuthorizationItem.builder().procedureId(2534L).bodySite("43").build();
        PreAuthorizationItem tooth34 = PreAuthorizationItem.builder().procedureId(2534L).bodySite("34").build();

        assertThat(PreAuthorizationItemMatcher.find(List.of(tooth43, tooth34), toothLine(1513L, "34"))).contains(tooth34);
        assertThat(PreAuthorizationItemMatcher.find(List.of(tooth43, tooth34), toothLine(1512L, "43"))).contains(tooth43);
        assertThat(PreAuthorizationItemMatcher.matches(tooth43, toothLine(1513L, "34"))).isFalse();
    }

    private static PatientServiceAndProduct toothLine(Long id, String tooth) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .billingItemType(BillingItemTypes.PROCEDURE)
                .serviceSource(ServiceSource.DENTAL_PROCEDURE)
                .sourceId(17L)
                .procedureId(2534L)
                .toothNumber(tooth)
                .build();
        line.setId(id);
        return line;
    }
}
