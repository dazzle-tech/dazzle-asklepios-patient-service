package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.CatalogClient;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogHelper {

    private final CatalogClient catalogClient;


    public CatalogHelper(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    public List<DiagnosticTestSetupDTO> getTestsByCatalog(Long catalogId) {
        try {
            return catalogClient.listAllTestByCatalog(catalogId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "tests by Catalog not found: " + catalogId,
                    "Catalog",
                    "notfound"
            );
        }
    }
}
