package com.dazzle.asklepios.config;

import com.dazzle.asklepios.web.rest.filter.RequestIdFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Forwards the current request id to downstream services so one request can be followed across services.
 */
@Component
public class RequestIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID);

        if (StringUtils.hasText(requestId)) {
            template.header(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }
    }
}
