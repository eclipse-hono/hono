package org.eclipse.hono.adapter.rest;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestProtocolAdapter extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RestProtocolAdapter.class);

    // Members

    private final HttpRequestMapping httpRequestMapping;

    private final int port;

    // Constructors

    public RestProtocolAdapter(HttpRequestMapping httpRequestMapping) {
        this(httpRequestMapping, 0);
    }

    public RestProtocolAdapter(HttpRequestMapping httpRequestMapping, int port) {
        this.httpRequestMapping = httpRequestMapping;
        this.port = port;
    }

    // Routes

    @Override
    public void configure() throws Exception {
        LOG.debug("Started REST protocol adapter at port {}.", port);
        from("netty4-http:http://0.0.0.0:" + port + "/?matchOnUriPrefix=true&httpMethodRestrict=PUT")
                .setExchangePattern(ExchangePattern.InOnly)
                .process(httpRequestMapping::mapRequest).to("amqp:telemetry");
    }

}