/**
 * Copyright (c) 2016 Red Hat.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hono.adapter.rest;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.ExchangePattern.InOnly;

/**
 * Exposes Camel REST endpoint (using Netty HTTP) and maps incoming requests/responses into AMQP messages. Can be used
 * to expose AMQP-based services using REST API.
 */
public class RestProtocolAdapter extends RouteBuilder {

    // Logger

    private static final Logger LOG = LoggerFactory.getLogger(RestProtocolAdapter.class);

    // Constants

    public static final String HONO_IN_ONLY = "HONO_IN_ONLY";

    // Members

    private final HttpRequestMapping httpRequestMapping;

    private final String host;

    private final int port;

    // Constructors


    public RestProtocolAdapter(HttpRequestMapping httpRequestMapping, String host, int port) {
        this.httpRequestMapping = httpRequestMapping;
        this.host = host;
        this.port = port;
    }

    public RestProtocolAdapter(HttpRequestMapping httpRequestMapping, int port) {
        this(httpRequestMapping, "0.0.0.0", port);
    }

    public RestProtocolAdapter(HttpRequestMapping httpRequestMapping) {
        this(httpRequestMapping, "0.0.0.0", 0);
    }

    // Routes

    @Override
    public void configure() throws Exception {
        LOG.debug("Started REST protocol adapter at port {}.", port);
            from("netty4-http:http://" + host + ":" + port + "/?matchOnUriPrefix=true&httpMethodRestrict=OPTIONS,GET,POST,PUT,DELETE").
                choice().
                    when(header(HONO_IN_ONLY).isEqualToIgnoreCase("true")).setExchangePattern(InOnly).
                end().
                choice().
                    when(header(HTTP_METHOD).isEqualTo("OPTIONS")).setBody().constant("").endChoice().
                otherwise().
                    process(httpRequestMapping::mapRequest).toD("${property.target}").process(httpRequestMapping::mapResponse).endChoice().
                end().
                setHeader("Access-Control-Allow-Origin").constant("*").
                setHeader("Access-Control-Allow-Headers").constant("Origin, X-Requested-With, Content-Type, Accept");
    }

}