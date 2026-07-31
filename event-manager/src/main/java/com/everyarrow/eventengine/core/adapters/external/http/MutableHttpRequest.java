package com.everyarrow.eventengine.core.adapters.external.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * A mutable, re-readable servlet request.
 *
 * Jetty (and most servlet containers) only allow the HTTP request body
 * to be consumed once. Since Event-Engine may run:
 *
 *   • security validation
 *   • monitoring signature checks
 *   • JSON reformatting / mutation
 *   • multiple hook passes
 *
 * We wrap the original request and store the body in memory, allowing:
 *
 *   - getInputStream() to be called multiple times
 *   - getReader() to be called multiple times
 *   - body mutation via setBody()
 */
public class MutableHttpRequest extends HttpServletRequestWrapper {

    private byte[] body;

    public MutableHttpRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = HttpRequestBodyReader.read(request);
    }

    /** Replace the HTTP body (used by security filters and hooks). */
    public void setBody(byte[] newBody) {
        this.body = newBody;
    }

    /** Access to the underlying raw servlet request if needed later. */
    public HttpServletRequest getOriginalRequest() {
        return (HttpServletRequest) super.getRequest();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);

        return new ServletInputStream() {
            @Override
            public int read() {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // No async IO support needed
            }
        };
    }

    @Override
    public BufferedReader getReader() throws UnsupportedEncodingException {
        return new BufferedReader(
                new InputStreamReader(getInputStream(), getCharsetOrUtf8())
        );
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    private String getCharsetOrUtf8() {
        return getOriginalRequest().getCharacterEncoding() != null
                ? getOriginalRequest().getCharacterEncoding()
                : StandardCharsets.UTF_8.name();
    }

    /** Returns the body as a UTF-8 string. */
    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
