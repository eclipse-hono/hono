/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.client.impl.CommandTargetMapperImpl;
import org.eclipse.hono.util.RegistrationConstants;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A component for determining where a incoming command message should be targeted at when processing the message in a
 * protocol adapter.
 * <p>
 * This refers to finding the <em>protocol adapter instance</em> that a device or gateway has connected to in order to
 * receive a command message. Furthermore, it is determined to which <em>gateway</em> a command is to be mapped to or
 * whether it will be sent to the target device directly.
 * <p>
 * For obtaining both information, the <em>getCommandHandlingAdapterInstances</em> operation of the Device Connection
 * service is used. See the <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API
 * specification</a> for details on that operation. Note that a possible result of multiple entries of that operation
 * will be reduced to just one finding here.
 * <p>
 * The <em>CommandTargetMapper</em> will return a gateway id as target, if there is no command consumer registered
 * specifically for the device id of the command, but instead there is a consumer registered for a gateway that may act
 * on behalf of the device.
 * <p>
 * Note that this also means that if a <em>gateway</em> has registered itself as a command consumer for a <em>specific
 * device</em> (instead of as a consumer for commands to <em>any</em> device that the gateway may handle), that gateway
 * won't be returned here for that device. That kind of gateway mapping will occur when processing the command at the
 * target protocol adapter instance.
 */
@Deprecated
public interface CommandTargetMapper {

    /**
     * Access to collaborators that the mapper needs for doing its work.
     *
     */
    interface CommandTargetMapperContext {

        /**
         * Gets the device identifiers of the gateways that an edge device may connect via.
         *
         * @param tenant The tenant that the device belongs to.
         * @param deviceId The device id.
         * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
         *            Implementing classes should use this as the parent for any span they create for tracing the execution
         *            of this operation.
         * @return The gateway identifiers.
         * @throws NullPointerException if any of the parameters except context is {@code null}.
         */
        Future<List<String>> getViaGateways(String tenant, String deviceId, SpanContext context);

        /**
         * Gets information about the adapter instances that can handle a command for the given device.
         * <p>
         * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API
         * specification</a> for a detailed description of the method's behaviour and the returned JSON object.
         * <p>
         * If no adapter instances are found, the returned future is failed.
         *
         * @param tenant The tenant that the device belongs to.
         * @param deviceId The device id.
         * @param viaGateways The list of gateways that may act on behalf of the given device.
         * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
         *            Implementing classes should use this as the parent for any span they create for tracing the execution
         *            of this operation.
         * @return A future indicating the outcome of the operation.
         *         <p>
         *         If instances were found, the future will be succeeded with a JSON object containing one or more mappings
         *         from device id to adapter instance id. Otherwise the future will be failed with a
         *         {@link org.eclipse.hono.client.ServiceInvocationException}.
         * @throws NullPointerException if any of the parameters except context is {@code null}.
         */
        Future<JsonObject> getCommandHandlingAdapterInstances(
                String tenant,
                String deviceId,
                List<String> viaGateways,
                SpanContext context);
    }

    /**
     * Creates a new {@link CommandTargetMapper} using the default implementation.
     *
     * @param tracer The tracer instance.
     * @return The CommandTargetMapper instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    static CommandTargetMapper create(final Tracer tracer) {
        return new CommandTargetMapperImpl(tracer);
    }

    /**
     * Creates a mapper context for client factories.
     *
     * @param registrationClientFactory The factory for creating Device Registration service clients.
     * @param deviceConnectionClientFactory The factory for creating Device Connection service clients.
     * @return The mapper context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    static CommandTargetMapperContext createContext(
            final RegistrationClientFactory registrationClientFactory,
            final BasicDeviceConnectionClientFactory deviceConnectionClientFactory) {

        Objects.requireNonNull(registrationClientFactory);
        Objects.requireNonNull(deviceConnectionClientFactory);

        return new CommandTargetMapperContext() {

            @Override
            public Future<List<String>> getViaGateways(
                    final String tenant,
                    final String deviceId,
                    final SpanContext context) {

                Objects.requireNonNull(tenant);
                Objects.requireNonNull(deviceId);

                return registrationClientFactory.getOrCreateRegistrationClient(tenant)
                        .compose(client -> client.assertRegistration(deviceId, null, context))
                        .map(json -> Optional.ofNullable(json.getJsonArray(RegistrationConstants.FIELD_VIA))
                                .map(array -> array.stream()
                                        .filter(String.class::isInstance)
                                        .map(String.class::cast)
                                        .collect(Collectors.toList()))
                                .orElse(Collections.emptyList()));
            }

            @Override
            public Future<JsonObject> getCommandHandlingAdapterInstances(
                    final String tenant, 
                    final String deviceId,
                    final List<String> viaGateways,
                    final SpanContext context) {

                Objects.requireNonNull(tenant);
                Objects.requireNonNull(deviceId);
                Objects.requireNonNull(viaGateways);

                return deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(tenant)
                        .compose(client -> client.getCommandHandlingAdapterInstances(deviceId, viaGateways, context));
            }
        };
    }

    /**
     * Initializes the mapper with the given context.
     *
     * @param context The context that the mapper needs for doing its work.
     * @throws NullPointerException if context is {@code null}.
     */
    void initialize(CommandTargetMapperContext context);

    /**
     * Determines the target protocol adapter instance for a command directed at the given device. Also determines
     * whether the command should be mapped to a gateway.
     * <p>
     * The mapping to a gateway will be done if there is no command consumer registered specifically for the given
     * device id, but instead there is a consumer registered for a gateway that may act on behalf of the device.
     * <p>
     * Note that this also means that if a <em>gateway</em> has registered itself as a command consumer specifically for
     * the given device (instead of as a consumer for commands to <em>any</em> device that the gateway may handle), that
     * gateway won't be returned here. That kind of gateway mapping will have to occur when processing the command at
     * the target protocol adapter instance.
     * <p>
     * If there are multiple command subscriptions from gateways that may act on behalf of the device and if the device
     * hasn't communicated via any of these gateways yet, this method chooses one of the possible combinations of
     * gateway and adapter instance where that gateway has subscribed for commands.
     * <p>
     * The value of the returned future is a JSON object with the fields
     * {@link org.eclipse.hono.util.DeviceConnectionConstants#FIELD_PAYLOAD_DEVICE_ID} and
     * {@link org.eclipse.hono.util.DeviceConnectionConstants#FIELD_ADAPTER_INSTANCE_ID} set to the determined values.
     * If the command is not mapped to a gateway here, the
     * {@link org.eclipse.hono.util.DeviceConnectionConstants#FIELD_PAYLOAD_DEVICE_ID} contains the given device
     * id itself.
     * <p>
     * Note that {@link #initialize(CommandTargetMapperContext)} has to have been called already,
     * otherwise a failed future is returned.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param context The currently active OpenTracing span context or {@code null}.
     * @return A succeeded Future containing the JSON object with target device/gateway and adapter instance; or a
     *         failed Future with:
     *         <ul>
     *         <li>a {@link ClientErrorException} with status <em>Not Found</em> if no matching adapter instance was
     *         found</li>
     *         <li>or a {@link ServiceInvocationException} with an error code indicating the cause of the failure</li>
     *         </ul>
     * @throws NullPointerException if tenantId or deviceId is {@code null}.
     */
    Future<JsonObject> getTargetGatewayAndAdapterInstance(String tenantId, String deviceId, SpanContext context);

}
