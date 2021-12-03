/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.service.http;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

/**
 * Decorate server span at different stages. Do not call blocking code inside decorators!
 * <p>
 * This class has been copied from the <a href="https://github.com/opentracing-contrib/java-vertx-web">
 * OpenTracing Vert.x Web Instrumentation</a> project.
 * It has been adapted to support Opentracing 0.33 and slightly adapted to Hono's code style guide.
 * There have also been minor improvements to the StandardTags inner class.
 *
 * @author Pavol Loffay
 */
public interface WebSpanDecorator {
    /**
     * Decorate span when span is started.
     *
     * @param request server request
     * @param span server span
     */
    void onRequest(HttpServerRequest request, Span span);

    /**
     * Decorate span when span is rerouted.
     *
     * @param request server request
     * @param span server span
     */
    void onReroute(HttpServerRequest request, Span span);

    /**
     * Decorate span when the response is known. This is effectively invoked in BodyEndHandler which is added to
     * - {@link io.vertx.ext.web.RoutingContext#addBodyEndHandler(io.vertx.core.Handler)}
     *
     * @param request server request
     * @param span server span
     */
    void onResponse(HttpServerRequest request, Span span);

    /**
     * Decorate request when an exception is thrown during request processing.
     *
     * @param throwable an exception thrown when processing the request
     * @param response server response
     * @param span server span
     */
    void onFailure(Throwable throwable, HttpServerResponse response, Span span);

    /**
     * Decorator which adds standard set of tags e.g. HTTP/PEER/ERROR tags.
     */
    class StandardTags implements WebSpanDecorator {
        @Override
        public void onRequest(final HttpServerRequest request, final Span span) {
            Tags.COMPONENT.set(span, "vertx");
            Tags.HTTP_METHOD.set(span, request.method().toString());
            Tags.HTTP_URL.set(span, HttpUtils.getAbsoluteURI(request));
        }

        @Override
        public void onReroute(final HttpServerRequest request, final Span span) {
            final Map<String, String> logs = new HashMap<>(2);
            logs.put("event", "reroute");
            logs.put(Tags.HTTP_URL.getKey(), HttpUtils.getAbsoluteURI(request));
            logs.put(Tags.HTTP_METHOD.getKey(), request.method().toString());
            span.log(logs);
        }

        @Override
        public void onResponse(final HttpServerRequest request, final Span span) {
            Tags.HTTP_STATUS.set(span, request.response().getStatusCode());
        }

        @Override
        public void onFailure(final Throwable throwable, final HttpServerResponse response, final Span span) {
            Tags.ERROR.set(span, Boolean.TRUE);

            if (throwable != null) {
                span.log(exceptionLogs(throwable));
            }
        }

        static Map<String, Object> exceptionLogs(final Throwable throwable) {
            final Map<String, Object> errorLog = new HashMap<>(2);
            errorLog.put("event", Tags.ERROR.getKey());
            errorLog.put("error.object", throwable);

            return errorLog;
        }
    }
}
