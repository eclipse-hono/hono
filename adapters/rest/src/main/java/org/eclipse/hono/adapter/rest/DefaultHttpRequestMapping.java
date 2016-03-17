package org.eclipse.hono.adapter.rest;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.commons.lang3.StringUtils.removeEnd;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DefaultHttpRequestMapping implements HttpRequestMapping {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpRequestMapping.class);

    // Constants

    public static final String DEFAULT_CONTENT_TYPE = "application/json";

    private Optional<PayloadEncoder> payloadEncoder;

    private final String contentType;

    // Constructors

    public DefaultHttpRequestMapping(Optional<PayloadEncoder> payloadEncoder, String contentType) {
        this.payloadEncoder = payloadEncoder;
        this.contentType = contentType;
    }

    public DefaultHttpRequestMapping(Optional<PayloadEncoder> payloadEncoder) {
        this(payloadEncoder, DEFAULT_CONTENT_TYPE);
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

        byte[] body = exc.getIn().getBody(byte[].class);
        if (payloadEncoder.isPresent() && body.length > 0) {
            Object payload = payloadEncoder.get().decode(body);
            exc.getIn().setBody(payload);
        }
    }

    @Override
    public void mapResponse(Exchange exchange) {
        if(payloadEncoder.isPresent()) {
            byte[] payload = payloadEncoder.get().encode(exchange.getIn().getBody());
            exchange.getIn().setBody(payload);
        }
    }

}
