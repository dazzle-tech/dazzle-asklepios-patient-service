package com.dazzle.asklepios.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.List;

/**
 * Log filter to prevent attackers from forging log entries by submitting input containing CRLF characters.
 * CRLF characters are replaced with a red colored _ character.
 *
 * @see <a href="https://owasp.org/www-community/attacks/Log_Injection">Log Forging Description</a>
 */
public class CRLFLogConverter extends CompositeConverter<ILoggingEvent> {

    public static final Marker CRLF_SAFE_MARKER = MarkerFactory.getMarker("CRLF_SAFE");

    private static final String[] SAFE_LOGGERS = {
        "org.hibernate",
        "org.springframework.boot.autoconfigure",
        "org.springframework.boot.diagnostics",
    };

    @Override
    protected String transform(ILoggingEvent event, String in) {
        List<Marker> markers = event.getMarkerList();
        if ((markers != null && !markers.isEmpty() && markers.get(0).contains(CRLF_SAFE_MARKER)) || isLoggerSafe(event)) {
            return in;
        }
        return in.replaceAll("[\n\r\t]", "_");
    }

    protected boolean isLoggerSafe(ILoggingEvent event) {
        for (String safeLogger : SAFE_LOGGERS) {
            if (event.getLoggerName().startsWith(safeLogger)) {
                return true;
            }
        }
        return false;
    }
}
