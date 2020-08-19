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

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;

/**
 * Contains information required during the processing of an MQTT CONNECT packet pertaining to an authenticated
 * connection attempt.
 */
public final class MqttConnectContext extends MapBasedExecutionContext {

    private final MqttEndpoint deviceEndpoint;
    private final TenantObject tenantObject;
    private final String authId;

    private MqttConnectContext(final MqttEndpoint deviceEndpoint, final TenantObject tenantObject, final String authId) {
        this.deviceEndpoint = Objects.requireNonNull(deviceEndpoint);
        this.tenantObject = Objects.requireNonNull(tenantObject);
        this.authId = Objects.requireNonNull(authId);
    }

    /**
     * Creates a new context for an authenticated connection attempt.
     *
     * @param endpoint The endpoint representing the client's connection attempt.
     * @param requestTenantDetailsProvider The provider to determine tenant and authentication identifier from the given
     *            endpoint.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future containing the created context. The future will be failed if there was an error getting the
     *         tenant and authentication identifier via the requestTenantDetailsProvider. In particular, the future will
     *         be failed with a {@link ClientErrorException} if there was no corresponding data given in the connection
     *         attempt to determine the tenant.
     * @throws NullPointerException if endpoint or requestTenantDetailsProvider is {@code null}.
     */
    public static Future<MqttConnectContext> fromConnectPacket(
            final MqttEndpoint endpoint,
            final MqttRequestTenantDetailsProvider requestTenantDetailsProvider,
            final SpanContext spanContext) {

        return requestTenantDetailsProvider.getFromMqttEndpoint(endpoint, spanContext)
                    .map(result -> new MqttConnectContext(endpoint, result.getTenantObject(), result.getAuthId()));
    }

    /**
     * Creates a new context for an authenticated connection attempt.
     *
     * @param endpoint The endpoint representing the client's connection attempt.
     * @param tenantObject The tenant that the device belongs to that opened the connection.
     * @param authId The authentication identifier used in the connection attempt.
     * @return The context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static MqttConnectContext fromConnectPacket(final MqttEndpoint endpoint, final TenantObject tenantObject,
            final String authId) {
        return new MqttConnectContext(endpoint, tenantObject, authId);
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
     * Gets the tenant that the device belongs to that opened the connection.
     *
     * @return The tenant object.
     */
    public TenantObject tenantObject() {
        return tenantObject;
    }

    /**
     * Gets the authentication identifier used in the MQTT connection.
     *
     * @return The authentication identifier.
     */
    public String authId() {
        return authId;
    }
}
