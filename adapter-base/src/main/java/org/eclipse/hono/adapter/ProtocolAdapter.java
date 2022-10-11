/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter;

import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A component which adapts a device's transport protocol to Hono's messaging infrastructure.
 */
public interface ProtocolAdapter {

    /**
     * Gets this adapter's type name.
     * <p>
     * The name should be unique among all protocol adapters that are part of a Hono installation. There is no specific
     * scheme to follow but it is recommended to include the adapter's origin and the protocol that the adapter supports
     * in the name and to use lower case letters only.
     * <p>
     * Based on this recommendation, Hono's standard HTTP adapter for instance might report <em>hono-http</em> as its
     * type name.
     * <p>
     * The name returned by this method is added to message that are forwarded to downstream consumers.
     *
     * @return The adapter's name.
     */
    String getTypeName();

    /**
     * Gets default properties for downstream telemetry and event messages.
     *
     * @param context The execution context for processing the downstream message.
     * @return The properties.
     */
    Map<String, Object> getDownstreamMessageProperties(TelemetryExecutionContext context);

    /**
     * Gets the client used for accessing the Tenant service.
     *
     * @return The client.
     */
    TenantClient getTenantClient();

    /**
     * Gets the client used for accessing the Credentials service.
     *
     * @return The client.
     */
    CredentialsClient getCredentialsClient();

    /**
     * Gets the client being used for sending telemetry messages downstream.
     *
     * @param tenant The tenant for which to send telemetry messages.
     * @return The sender.
     */
    TelemetrySender getTelemetrySender(TenantObject tenant);

    /**
     * Gets the client being used for sending events downstream.
     *
     * @param tenant The tenant for which to send events.
     * @return The sender.
     */
    EventSender getEventSender(TenantObject tenant);

    /**
     * Gets the factory used for creating clients to receive commands.
     *
     * @return The factory.
     */
    ProtocolAdapterCommandConsumerFactory getCommandConsumerFactory();

    /**
     * Gets the client being used for sending command response messages downstream.
     * <p>
     * Per default, the client using the given messaging type will be chosen. Only if
     * no such client is available, the client will be determined using the messaging
     * type configured for the given tenant (if set) or using the overall default type.
     *
     * @param messagingType The type of messaging system to use.
     * @param tenant The tenant for which to send messages.
     * @return The sender.
     */
    CommandResponseSender getCommandResponseSender(MessagingType messagingType, TenantObject tenant);

        /**
     * Gets an assertion of a device's registration status.
     * <p>
     * Note that this method will also update the last gateway associated with
     * the given device (if applicable).
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to get the assertion for.
     * @param authenticatedDevice The device that has authenticated to this protocol adapter.
     *            <p>
     *            If not {@code null} then the authenticated device is compared to the given tenant and device ID. If
     *            they differ in the device identifier, then the authenticated device is considered to be a gateway
     *            acting on behalf of the device.
     * @param context The currently active OpenTracing span that is used to
     *                trace the retrieval of the assertion.
     * @return A succeeded future containing the assertion or a future
     *         failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if the
     *         device's registration status could not be asserted.
     * @throws NullPointerException if any of tenant or device ID are {@code null}.
     */
    Future<RegistrationAssertion> getRegistrationAssertion(
            String tenantId,
            String deviceId,
            Device authenticatedDevice,
            SpanContext context);

    /**
     * Sends an <em>empty notification</em> containing a given <em>time until disconnect</em> for
     * a device.
     *
     * @param tenant The tenant that the device belongs to, who owns the device.
     * @param deviceId The device for which the TTD is reported.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @param ttd The time until disconnect (seconds).
     * @param context The currently active OpenTracing span that is used to
     *                trace the sending of the event.
     * @return A future indicating the outcome of the operation. The future will be
     *         succeeded if the TTD event has been sent downstream successfully.
     *         Otherwise, it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of tenant, device ID or TTD are {@code null}.
     */
    Future<Void> sendTtdEvent(
            String tenant,
            String deviceId,
            Device authenticatedDevice,
            Integer ttd,
            SpanContext context);

    /**
     * Checks if this adapter is enabled for a given tenant, requiring the tenant itself to be enabled as well.
     *
     * @param tenantConfig The tenant to check for.
     * @return A succeeded future if the given tenant and this adapter are enabled.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ClientErrorException}
     *         containing the 403 Forbidden status code.
     * @throws NullPointerException if tenant config is {@code null}.
     */
    Future<TenantObject> isAdapterEnabled(TenantObject tenantConfig);

    /**
     * Checks if a tenant's message limit will be exceeded by a given payload.
     *
     * @param tenantConfig The tenant to check the message limit for.
     * @param payloadSize  The size of the message payload in bytes.
     * @param spanContext The currently active OpenTracing span context that is used to
     *                    trace the limits verification or {@code null}
     *                    if no span is currently active.
     * @return A succeeded future if the message limit has not been reached yet
     *         or if the limits could not be checked.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ClientErrorException}
     *         containing the 429 Too many requests status code.
     * @throws NullPointerException if tenant is {@code null}.
     */
    Future<Void> checkMessageLimit(
            TenantObject tenantConfig,
            long payloadSize,
            SpanContext spanContext);

    /**
     * Gets the number of seconds after which this protocol adapter should give up waiting for an upstream command for a
     * device of a given tenant.
     * <p>
     * Protocol adapters may override this method to e.g. use a static value for all tenants.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceTtd The TTD value provided by the device in seconds or {@code null} if the device did not
     *                  provide a TTD value.
     * @return A succeeded future that contains {@code null} if device TTD is {@code null}, or otherwise the lesser of
     *         device TTD and the value returned by {@link TenantObject#getMaxTimeUntilDisconnect(String)}.
     * @throws NullPointerException if tenant is {@code null}.
     */
    default Future<Integer> getTimeUntilDisconnect(final TenantObject tenant, final Integer deviceTtd) {

        Objects.requireNonNull(tenant);

        if (deviceTtd == null) {
            return Future.succeededFuture();
        } else {
            return Future.succeededFuture(Math.min(tenant.getMaxTimeUntilDisconnect(getTypeName()), deviceTtd));
        }
    }
}
