/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.coap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.auth.AdditionalInfo;
import org.eclipse.californium.elements.auth.ExtensiblePrincipal;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * A AbstractHonoResourceTest.
 *
 */
@ExtendWith(VertxExtension.class)
class AbstractHonoResourceTest extends ResourceTestBase {

    private static final String SPAN_NAME = "test";

    private AbstractHonoResource givenAResource(final CoapProtocolAdapter adapter) {

        return new AbstractHonoResource("test", adapter, NoopTracerFactory.create(), vertx) {
            @Override
            protected String getSpanName(final Exchange exchange) {
                return SPAN_NAME;
            }
        };
    }

    private Request newRequest(final String uri, final DeviceUser authenticatedDevice) {

        final var sourceContext = mock(EndpointContext.class);
        Optional.ofNullable(authenticatedDevice).ifPresent(device -> {
            final var info = AdditionalInfo.from(Map.of(DeviceInfoSupplier.EXT_INFO_KEY_HONO_DEVICE, device));
            @SuppressWarnings("unchecked")
            final ExtensiblePrincipal<Principal> peerIdentity = mock(ExtensiblePrincipal.class);
            when(peerIdentity.getExtendedInfo()).thenReturn(info);
            when(sourceContext.getPeerIdentity()).thenReturn(peerIdentity);
        });
        final var options = new OptionSet();
        options.setUriPath(uri);
        final Request request = mock(Request.class);
        when(request.getType()).thenReturn(Type.CON);
        when(request.isConfirmable()).thenReturn(true);
        when(request.getOptions()).thenReturn(options);
        when(request.getSourceContext()).thenReturn(sourceContext);
        return request;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "true,/telemetry/DEFAULT_TENANT/device_1",
            "false,/telemetry/DEFAULT_TENANT/device_1",
            "true,/telemetry//device_1",
            "true,/event/DEFAULT_TENANT/device_1",
            "false,/event/DEFAULT_TENANT/device_1",
            "true,/event//device_1",
            "true,/command_response/DEFAULT_TENANT/device_1/request_id",
            "false,/command_response/DEFAULT_TENANT/device_1/request_id",
            "true,/command_response//device_1/request_id"
            })
    void testGetPutRequestDeviceAndAuthSucceeds(
            final boolean isDeviceAuthenticated,
            final String uri,
            final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        final var resource = givenAResource(adapter);
        final var request = newRequest(uri, isDeviceAuthenticated ? new DeviceUser("DEFAULT_TENANT", "device_1") : null);
        final var exchange = newCoapExchange(Buffer.buffer(), request);
        resource.getPutRequestDeviceAndAuth(exchange)
            .onComplete(ctx.succeeding(r -> {
                ctx.verify(() -> {
                    assertThat(r.getOriginDevice().getTenantId().equals("DEFAULT_TENANT"));
                    assertThat(r.getOriginDevice().getDeviceId().equals("device_1"));
                });
                ctx.completeNow();
            }));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "true,/telemetry/OTHER_TENANT/device_1,403",
            "true,/telemetry/OTHER_TENANT/,404",
            "false,/telemetry//device_1,404",
            "false,/telemetry//,404",
            "true,/telemetry//,404",
            "true,/telemetry/DEFAULT_TENANT/,404",
            "true,/event/OTHER_TENANT/device_1,403",
            "false,/event//device_1,404",
            "true,/command_response/OTHER_TENANT/device_1/request_id,403",
            "false,/event//device_1/request_id,404"
            })
    void testGetPutRequestDeviceAndAuthFailsForInvalidUri(
            final boolean isDeviceAuthenticated,
            final String uri,
            final int expectedErrorCode,
            final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        final var resource = givenAResource(adapter);
        final var request = newRequest(uri, isDeviceAuthenticated ? new DeviceUser("DEFAULT_TENANT", "device_1") : null);
        final var exchange = newCoapExchange(Buffer.buffer(), request);
        resource.getPutRequestDeviceAndAuth(exchange)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(expectedErrorCode);
                });
                ctx.completeNow();
            }));
    }
}
