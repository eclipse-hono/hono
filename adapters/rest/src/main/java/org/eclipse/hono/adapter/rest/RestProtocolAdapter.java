package org.eclipse.hono.adapter.rest;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.ExchangePattern.InOnly;

public class RestProtocolAdapter extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RestProtocolAdapter.class);

    // Constants

    public static final String HONO_IN_ONLY = "HONO_IN_ONLY";

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
            from("netty4-http:http://0.0.0.0:" + port + "/?matchOnUriPrefix=true&httpMethodRestrict=OPTIONS,GET,POST,PUT,DELETE").
                choice().
                    when(header(HONO_IN_ONLY).isEqualToIgnoreCase("true")).setExchangePattern(InOnly).
                end().
                choice().
                    when(header(HTTP_METHOD).isEqualTo("OPTIONS")).setBody().constant("").endChoice().
                otherwise().
                    process(httpRequestMapping::mapRequest).toD("${property.target}").endChoice().
                end().
                setHeader("Access-Control-Allow-Origin").constant("*").
                setHeader("Access-Control-Allow-Headers").constant("Origin, X-Requested-With, Content-Type, Accept");
    }

}