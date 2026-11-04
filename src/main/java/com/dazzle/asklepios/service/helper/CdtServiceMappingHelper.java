package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.CdtServiceMappingClient;
import com.dazzle.asklepios.client.setup.dto.CdtServiceMappingDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CdtServiceMappingHelper {

    private final CdtServiceMappingClient cdtServiceMappingClient;

    public CdtServiceMappingHelper(CdtServiceMappingClient cdtServiceMappingClient) {
        this.cdtServiceMappingClient = cdtServiceMappingClient;
    }

    public List<CdtServiceMappingDTO> getServiceMappingsForCdt(Long cdtCodeId) {
        return cdtServiceMappingClient.getMappingsForCdt(cdtCodeId);
    }
}
