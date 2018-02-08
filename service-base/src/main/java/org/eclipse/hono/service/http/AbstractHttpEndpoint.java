/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.http;

import java.net.HttpURLConnection;
import java.util.Objects;

import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.hono.service.AbstractEndpoint;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.Vertx;


/**
 * Base class for HTTP based Hono endpoints.
 * 
 * @param <T> The type of configuration properties this endpoint understands.
 */
public abstract class AbstractHttpEndpoint<T> extends AbstractEndpoint implements HttpEndpoint {

    /**
     * The key that is used to put a valid JSON payload to the RoutingContext.
     */
    public static final String KEY_REQUEST_BODY = "KEY_REQUEST_BODY";

    /**
     * The configuration properties for this endpoint.
     */
    protected T config;

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    public AbstractHttpEndpoint(Vertx vertx) {
        super(vertx);
    }

    /**
     * Sets configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Qualifier(Constants.QUALIFIER_REST)
    @Autowired(required = false)
    public final void setConfiguration(final T props) {
        this.config = Objects.requireNonNull(props);
    }

    /**
     * Check the Content-Type of the request to be 'application/json' and extract the payload if this check was
     * successful.
     * <p>
     * The payload is parsed to ensure it is valid JSON and is put to the RoutingContext ctx with the
     * key {@link #KEY_REQUEST_BODY}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected void extractRequiredJsonPayload(final RoutingContext ctx) {

        final MIMEHeader contentType = ctx.parsedHeaders().contentType();
        if (contentType == null) {
            ctx.response().setStatusMessage("Missing Content-Type header");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else if (!HttpUtils.CONTENT_TYPE_JSON.equalsIgnoreCase(contentType.value())) {
            ctx.response().setStatusMessage("Unsupported Content-Type");
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else {
            try {
                if (ctx.getBody() != null) {
                    ctx.put(KEY_REQUEST_BODY, ctx.getBodyAsJson());
                    ctx.next();
                } else {
                    ctx.response().setStatusMessage("Empty body");
                    ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
                }
            } catch (DecodeException e) {
                ctx.response().setStatusMessage("Invalid JSON");
                ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
            }
        }
    }

}
