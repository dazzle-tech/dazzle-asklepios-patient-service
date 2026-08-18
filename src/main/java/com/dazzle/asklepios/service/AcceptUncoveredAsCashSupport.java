package com.dazzle.asklepios.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the doctor's confirmation that an uncovered insurance item
 * may be billed as cash. Frontend retries the same create/submit
 * call after the warning dialog with this header or query param.
 */
public final class AcceptUncoveredAsCashSupport {

    public static final String HEADER = "X-Accept-Uncovered-As-Cash";

    public static final String PARAM = "acceptUncoveredAsCash";

    private AcceptUncoveredAsCashSupport() {
    }

    public static boolean isAccepted(Boolean explicitFlag) {
        return Boolean.TRUE.equals(explicitFlag) || isAcceptedFromRequest();
    }

    public static boolean isAcceptedFromRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return false;
        }

        HttpServletRequest request = servletAttributes.getRequest();
        if (parseBoolean(request.getHeader(HEADER))) {
            return true;
        }

        return parseBoolean(request.getParameter(PARAM));
    }

    private static boolean parseBoolean(String value) {
        return value != null && Boolean.parseBoolean(value.trim());
    }
}
