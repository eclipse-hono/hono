package org.eclipse.hono.adapter.rest;

import org.apache.camel.Exchange;

public interface HttpRequestMapping {

    void mapRequest(Exchange exchange);

    void mapResponse(Exchange exchange);

}
