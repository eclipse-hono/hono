package org.eclipse.hono.adapter.rest;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.commons.lang3.StringUtils.removeEnd;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BinaryHttpRequestMapping implements HttpRequestMapping {

    private static final Logger LOG = LoggerFactory.getLogger(BinaryHttpRequestMapping.class);

    // Constants

    public static final String DEFAULT_CONTENT_TYPE = "application/json";

    private final String contentType;

    public BinaryHttpRequestMapping(String contentType) {
        this.contentType = contentType;
    }

    public BinaryHttpRequestMapping() {
        this(DEFAULT_CONTENT_TYPE);
    }

    // Operations

    @Override
    public void mapRequest(Exchange exc) {
        exc.getIn().setHeader(CONTENT_TYPE, contentType);

        String requestUri = exc.getIn().getHeader(HTTP_URI, String.class);
        LOG.debug("Processing request URI: {}", requestUri);
        String trimmedUri = removeEnd(requestUri, "/");
        LOG.debug("Trimmed request URI: {}", trimmedUri);
        String busChannel = trimmedUri.substring(1).replaceAll("\\/", ".");
        exc.setProperty("target", "amqp:" + busChannel);
        exc.getIn().setHeader("resource", trimmedUri);
    }

}
