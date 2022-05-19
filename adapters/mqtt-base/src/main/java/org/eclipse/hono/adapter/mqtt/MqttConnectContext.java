/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.util.Objects;
import java.util.OptionalInt;

import org.eclipse.hono.util.MapBasedExecutionContext;

import io.opentracing.Span;
import io.vertx.mqtt.MqttEndpoint;

/**
 * Contains information required during the processing of an MQTT CONNECT packet.
 */
public final class MqttConnectContext extends MapBasedExecutionContext {

    private final MqttEndpoint deviceEndpoint;
    private OptionalInt traceSamplingPriority = OptionalInt.empty();

    private MqttConnectContext(final Span span, final MqttEndpoint deviceEndpoint) {
        super(span);
        this.deviceEndpoint = Objects.requireNonNull(deviceEndpoint);
    }

    /**
     * Creates a new context for a connection attempt.
     *
     * @param endpoint The endpoint representing the client's connection attempt.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @return The context.
     * @throws NullPointerException if endpoint or span is {@code null}.
     */
    public static MqttConnectContext fromConnectPacket(final MqttEndpoint endpoint, final Span span) {
        return new MqttConnectContext(span, endpoint);
    }

    /**
     * Gets the MQTT endpoint over which the message has been
     * received.
     *
     * @return The endpoint.
     */
    public MqttEndpoint deviceEndpoint() {
        return deviceEndpoint;
    }

    /**
     * Gets the value for the <em>sampling.priority</em> span tag to be used for OpenTracing spans created in connection
     * with this context.
     *
     * @return An <em>OptionalInt</em> containing the value for the <em>sampling.priority</em> span tag or an empty
     *         <em>OptionalInt</em> if no priority should be set.
     */
    public OptionalInt getTraceSamplingPriority() {
        return traceSamplingPriority;
    }

    /**
     * Sets the value for the <em>sampling.priority</em> span tag to be used for OpenTracing spans created in connection
     * with this context.
     *
     * @param traceSamplingPriority The <em>OptionalInt</em> containing the <em>sampling.priority</em> span tag value or
     *            an empty <em>OptionalInt</em> if no priority should be set.
     * @throws NullPointerException if traceSamplingPriority is {@code null}.
     */
    public void setTraceSamplingPriority(final OptionalInt traceSamplingPriority) {
        this.traceSamplingPriority = Objects.requireNonNull(traceSamplingPriority);
    }
}
