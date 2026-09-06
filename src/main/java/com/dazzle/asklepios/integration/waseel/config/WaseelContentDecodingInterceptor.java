package com.dazzle.asklepios.integration.waseel.config;

import org.brotli.dec.BrotliInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Locale;

/**
 * Waseel's ELB returns {@code Content-Encoding: br} for Postman-like clients.
 * Java's default {@code HttpURLConnection} only auto-decodes gzip, so Jackson
 * then fails with "Error while extracting response ... application/json".
 */
public class WaseelContentDecodingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        String encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("br")) {
            return new BrotliDecodedResponse(response);
        }
        return response;
    }

    private static final class BrotliDecodedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private InputStream decodedBody;

        private BrotliDecodedResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            try {
                if (decodedBody != null) {
                    decodedBody.close();
                }
            } catch (IOException ignored) {
                // already closing
            }
            delegate.close();
        }

        @Override
        public InputStream getBody() throws IOException {
            if (decodedBody == null) {
                PushbackInputStream pushback = new PushbackInputStream(delegate.getBody(), 1);
                int first = pushback.read();
                if (first < 0) {
                    decodedBody = pushback;
                } else {
                    pushback.unread(first);
                    // Already JSON (some proxies keep the br header after decoding).
                    if (first == '{' || first == '[') {
                        decodedBody = pushback;
                    } else {
                        decodedBody = new BrotliInputStream(pushback);
                    }
                }
            }
            return decodedBody;
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(delegate.getHeaders());
            headers.remove(HttpHeaders.CONTENT_ENCODING);
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            return headers;
        }
    }
}
