package org.eclipse.hono.adapter.rest;

import org.apache.camel.Exchange;

public class AmqpNativeHttpRequestMapping implements HttpRequestMapping {

    @Override
    public void mapRequest(Exchange exchange) {
        // TODO: Use camel-vertx-proton to map incoming HTTP request to AMQP invocation
    }

}
