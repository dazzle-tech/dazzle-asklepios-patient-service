package com.dazzle.asklepios.client.radiologyImage;

import com.dazzle.asklepios.service.dto.radiology.PacsResponseDTO;
import com.dazzle.asklepios.service.dto.radiology.PacsStudyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PacsIntegrationService {

    private final RestTemplate restTemplate;

    @Value("${pacs.base-url}")
    private String baseUrl;

    @Value("${pacs.token}")
    private String token;

    public List<PacsStudyDTO> getStudiesByAccessionNumber(
            String accessionNumber
    ) {

        HttpHeaders headers = new HttpHeaders();

        headers.set(
                HttpHeaders.AUTHORIZATION,
                "Basic " + token
        );

        HttpEntity<Void> request =
                new HttpEntity<>(headers);

        String url =
                baseUrl
                        + "?accession_number=" + accessionNumber
                        + "&expires_in_hours=48";

        log.debug(
                "Calling PACS API. accessionNumber={}, url={}",
                accessionNumber,
                url
        );

        try {

            ResponseEntity<PacsResponseDTO> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            request,
                            PacsResponseDTO.class
                    );

            log.debug(
                    "PACS response status={}, body={}",
                    response.getStatusCode(),
                    response.getBody()
            );

            return response.getBody() != null
                    ? response.getBody().message()
                    : List.of();

        } catch (HttpClientErrorException.NotFound ex) {

            log.warn(
                    "No studies found for accession number={}",
                    accessionNumber
            );

            return List.of();
        }
    }
}