/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap.lwm2m;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.coap.CoapContext;
import org.eclipse.hono.adapter.coap.ResourceTestBase;
import org.eclipse.hono.auth.Device;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link LwM2MResourceDirectory}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class LwM2MResourceDirectoryTest extends ResourceTestBase {

    private LwM2MResourceDirectory resource;
    private Device device = new Device("tenant", "device");
    private LwM2MRegistrationStore store;

    /**
     * Sets up the resource under test.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    void createResource(final VertxTestContext ctx) {
        store = mock(LwM2MRegistrationStore.class);
        when(store.addRegistration(any(Registration.class), any())).thenReturn(Future.succeededFuture());
        when(store.updateRegistration(any(RegistrationUpdate.class), any())).thenReturn(Future.succeededFuture());
        when(store.removeRegistration(anyString(), any())).thenReturn(Future.succeededFuture());

        givenAnAdapter(properties);
        resource = new LwM2MResourceDirectory(adapter, store, NoopTracerFactory.create());
        resource.start().onComplete(ctx.completing());
    }

    private String givenARegisteredDevice(final Device device, final BindingMode bindingMode, final long lifetime) {
        final OptionSet options = new OptionSet();
        options.setUriPath("rd");
        options.setUriQuery(String.format("ep=test-device&lwm2m=1.0&b=%s&lt=%d", bindingMode.name(), lifetime));
        options.setContentFormat(MediaTypeRegistry.APPLICATION_LINK_FORMAT);
        final CoapExchange exchange = newCoapExchange(Buffer.buffer(), CoAP.Type.CON, options);
        final var context = CoapContext.fromRequest(exchange, device, device, "auth-id", span);

        resource.handlePost(context);

        // THEN the device has been registered
        final var registration = ArgumentCaptor.forClass(Registration.class);
        verify(store).addRegistration(registration.capture(), any());
        assertThat(registration.getValue().getBindingMode()).isEqualTo(bindingMode);
        assertThat(registration.getValue().getLifeTimeInSec()).isEqualTo(lifetime);
        // and a CoAP response with code 2.01 is sent back to the device
        final var response = ArgumentCaptor.forClass(Response.class);
        verify(exchange).respond(response.capture());
        assertThat(response.getValue().getCode()).isEqualTo(ResponseCode.CREATED);
        // that contains the registration ID in the location-path option
        final var locationPath = ArgumentCaptor.forClass(String.class);
        verify(exchange).setLocationPath(locationPath.capture());
        assertThat(locationPath.getValue()).isNotNull();
        // return registration ID
        return locationPath.getValue().substring(locationPath.getValue().lastIndexOf("/") + 1);
    }

    /**
     * Verifies that a device can successfully register using any of the supported binding modes.
     *
     * @param bindingMode The binding mode that the device uses when registering.
     * @param lifetime The lifetime that the device uses when registering.
     */
    @ParameterizedTest
    @CsvSource(value = { "U,84600", "UQ,20"})
    void testDeviceRegistersSuccessfully(final BindingMode bindingMode, final Integer lifetime) {

        givenARegisteredDevice(device, bindingMode, lifetime);
    }

    /**
     * Verifies that a device can successfully update its registration.
     */
    @Test
    void testDeviceUpdatesRegistrationSuccessfully() {

        // GIVEN a device that has registered with non-queue mode
        final var registrationId = givenARegisteredDevice(device, BindingMode.U, 84600);

        // WHEN the device sends a request to update its registration
        final OptionSet options = new OptionSet();
        options.setUriPath("rd/" + registrationId);
        options.setUriQuery("b=U&lt=10000");
        final CoapExchange exchange = newCoapExchange(Buffer.buffer(), CoAP.Type.CON, options);
        final var context = CoapContext.fromRequest(exchange, device, device, "auth-id", span);
        resource.handlePost(context);

        // THEN the registration in the store has been updated accordingly
        verify(store).updateRegistration(argThat(update -> update.getBindingMode() == BindingMode.U
                && update.getLifeTimeInSec() == 10000L), any());
        // and a CoAP response with code 2.04 is sent back to the device
        final var response = ArgumentCaptor.forClass(Response.class);
        verify(exchange).respond(response.capture());
        assertThat(response.getValue().getCode()).isEqualTo(ResponseCode.CHANGED);
    }

    /**
     * Verifies that a device can successfully de-register.
     */
    @Test
    void testDeviceDeregistersSuccessfully() {

        // GIVEN a registered device
        final var registrationId = givenARegisteredDevice(device, BindingMode.U, 84600);
        // WHEN the device deregisters
        final OptionSet options = new OptionSet();
        options.setUriPath("rd/" + registrationId);
        final CoapExchange exchange = newCoapExchange(Buffer.buffer(), CoAP.Type.CON, options);
        final var context = CoapContext.fromRequest(exchange, device, device, "auth-id", span);
        resource.handleDelete(context);

        // THEN the registration in the store has been removed
        verify(store).removeRegistration(eq(registrationId), any());
        // and a CoAP response with code 2.04 is sent back to the device
        final var response = ArgumentCaptor.forClass(Response.class);
        verify(exchange).respond(response.capture());
        assertThat(response.getValue().getCode()).isEqualTo(ResponseCode.DELETED);
    }
}
