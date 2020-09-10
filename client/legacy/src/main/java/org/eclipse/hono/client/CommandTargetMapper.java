/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.impl.CommandTargetMapperImpl;
import org.eclipse.hono.util.DeviceConnectionConstants;

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
public interface CommandTargetMapper {

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
     * Initializes the CommandTargetMapper with the given components.
     *
     * @param registrationClientFactory The factory to create a registration client instance. Note that no
     *            initialization of this factory will be done here, that is supposed to be done by the calling method.
     * @param deviceConnectionClientFactory The factory to create a device connection client instance. Note that no
     *            initialization of this factory will be done here, that is supposed to be done by the calling method.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void initialize(RegistrationClientFactory registrationClientFactory,
            BasicDeviceConnectionClientFactory deviceConnectionClientFactory);

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
     * {@link DeviceConnectionConstants#FIELD_PAYLOAD_DEVICE_ID} and
     * {@link DeviceConnectionConstants#FIELD_ADAPTER_INSTANCE_ID} set to the determined values. If the command is not
     * mapped to a gateway here, the {@link DeviceConnectionConstants#FIELD_PAYLOAD_DEVICE_ID} contains the given device
     * id itself.
     * <p>
     * Note that {@link #initialize(RegistrationClientFactory, BasicDeviceConnectionClientFactory)} has to have been
     * called already, otherwise a failed future is returned.
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
