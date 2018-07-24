/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.adapter.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.contrib.vertx.ext.web.WebSpanDecorator;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;


/**
 * A decorator which adds Hono component specific
 * tags to an OpenTracing span covering the processing of a request.
 *
 */
public class ComponentMetaDataDecorator extends WebSpanDecorator.StandardTags {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentMetaDataDecorator.class);

    private final Map<String, String> tags;

    /**
     * Creates a new decorator for default tags.
     */
    public ComponentMetaDataDecorator() {
        this(new HashMap<>(0));
    }

    /**
     * Creates a new decorator for default and custom tags.
     * 
     * @param customTags The tags that should be set on spans for incoming requests.
     * @throws NullPointerException if tags is {@code null}.
     */
    public ComponentMetaDataDecorator(final Map<String, String> customTags) {
        super();
        this.tags = Collections.unmodifiableMap(Objects.requireNonNull(customTags));
    }

    @Override
    public void onRequest(final HttpServerRequest request, final Span span) {
        LOG.trace("starting span for request [method: {}, URI: {}", request.method(), request.absoluteURI());
        Tags.HTTP_METHOD.set(span, request.method().toString());
        Tags.HTTP_URL.set(span, request.absoluteURI());
        tags.forEach((key, value) -> {
            span.setTag(key, value);
        });
    }

    @Override
    public void onReroute(final HttpServerRequest request, final Span span) {
        LOG.trace("logging re-routed request [method: {}, URI: {}", request.method(), request.absoluteURI());
        final Map<String, String> logs = new HashMap<>(3);
        logs.put(Fields.EVENT, "reroute");
        logs.put(Tags.HTTP_URL.getKey(), request.absoluteURI());
        logs.put(Tags.HTTP_METHOD.getKey(), request.method().toString());
        span.log(logs);
    }

    @Override
    public void onResponse(final HttpServerRequest request, final Span span) {
        LOG.trace("setting status code of response to request [method: {}, URI: {}", request.method(), request.absoluteURI());
        Tags.HTTP_STATUS.set(span, request.response().getStatusCode());
    }

    @Override
    public void onFailure(final Throwable throwable, final HttpServerResponse response, final Span span) {
        LOG.trace("logging failed processing of request");
        TracingHelper.logError(span, throwable);
    }
}
