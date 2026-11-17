package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.CdtDentalActionClient;
import com.dazzle.asklepios.client.setup.dto.CdtDentalActionDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CdtDentalActionHelper {

    private final CdtDentalActionClient cdtDentalActionClient;

    public CdtDentalActionHelper(CdtDentalActionClient cdtDentalActionClient) {
        this.cdtDentalActionClient = cdtDentalActionClient;
    }

    public List<CdtDentalActionDTO> getDentalActionsForCdt(Long cdtCodeId) {
        return cdtDentalActionClient.getDentalActionsForCdt(cdtCodeId);
    }
}
