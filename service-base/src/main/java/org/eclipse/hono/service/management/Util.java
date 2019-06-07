/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management;

import java.util.Objects;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.eclipse.hono.util.MessageHelper;

/**
 * Utility class for the management HTTP API.
 */
public class Util {

    /**
     * Prevent instantiation.
     */
    private Util(){}

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a service operation.
     * <p>
     * The returned span will already contain tags for the given tenant and device ids (if either is not {code null}).
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tracer the Tracer instance.
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param className The class name to insert in the Span.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    public static final Span newChildSpan(final String operationName, final SpanContext spanContext,
            final Tracer tracer, final String tenantId, final String deviceId, final String className) {
        Objects.requireNonNull(operationName);
        // we set the component tag to the class name because we have no access to
        // the name of the enclosing component we are running in
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName)
                .addReference(References.CHILD_OF, spanContext)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), className)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        if (tenantId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }
        if (deviceId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        }
        return spanBuilder.start();
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a tenant service operation.
     * <p>
     * The returned span will already contain tags for the given tenant id (if not {code null}).
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tracer the Tracer instance.
     * @param tenantId The tenant id.
     * @param className The class name to insert in the Span.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    public static final Span newChildSpan(final String operationName, final SpanContext spanContext,
            final Tracer tracer,
            final String tenantId, final String className) {
        return newChildSpan(operationName, spanContext, tracer, tenantId, null, className);
    }

}
