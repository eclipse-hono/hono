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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.Token;
import org.eclipse.californium.core.network.RandomTokenGenerator;
import org.eclipse.californium.core.network.TokenGenerator;
import org.eclipse.californium.core.network.TokenGenerator.Scope;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.observe.Observation;
import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.hono.adapter.coap.ResourceTestBase;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.leshan.core.Link;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.Identity;
import org.eclipse.leshan.server.californium.registration.CaliforniumRegistrationStore;
import org.eclipse.leshan.server.californium.registration.InMemoryRegistrationStore;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link LeshanBasedLwM2MRegistrationStore}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class LeshanBasedLwM2MRegistrationStoreTest extends ResourceTestBase {

    private final InetSocketAddress clientAddress = InetSocketAddress.createUnresolved("localhost", 15000);
    private final TokenGenerator tokenGenerator = new RandomTokenGenerator(NetworkConfig.createStandardWithoutFile());

    private LeshanBasedLwM2MRegistrationStore store;
    private Tracer tracer = NoopTracerFactory.create();
    private Identity identity;
    private CaliforniumRegistrationStore observationStore;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp(final VertxTestContext ctx) {
        givenAnAdapter(properties);
        observationStore = new InMemoryRegistrationStore();
        store = new LeshanBasedLwM2MRegistrationStore(observationStore, adapter, tracer);
        store.setResourcesToObserve(Map.of("/3/0", EndpointType.TELEMETRY));
        store.start().onComplete(ctx.completing());
        identity = Identity.psk(clientAddress, "device");
    }

    private Registration newRegistration(
            final String registrationId,
            final String endpoint,
            final String lwm2mVersion,
            final Integer lifetime,
            final BindingMode bindingMode) {

        final Link[] objectLinks = Link.parse("</3/0>".getBytes(StandardCharsets.UTF_8));

        return new Registration.Builder("reg-id", endpoint, identity)
                .lwM2mVersion(lwm2mVersion)
                .lifeTimeInSec(lifetime.longValue())
                .bindingMode(bindingMode)
                .objectLinks(objectLinks)
                .additionalRegistrationAttributes(Map.of(
                        TracingHelper.TAG_TENANT_ID.getKey(), "tenant",
                        TracingHelper.TAG_DEVICE_ID.getKey(), "device"))
                .build();
    }

    private Token assertObserveRelationEstablished(final String registrationId, final String resourcePath) {

        final LwM2mPath path = new LwM2mPath(resourcePath);
        // capture outbound observe request for the given resource
        final var observeRequest = ArgumentCaptor.forClass(Request.class);
        verify(secureEndpoint).sendRequest(observeRequest.capture());
        assertThat(observeRequest.getValue().getCode()).isEqualTo(Code.GET);
        assertThat(observeRequest.getValue().getOptions().hasObserve()).isTrue();
        assertThat(observeRequest.getValue().getOptions().getUriPathString()).isEqualTo(resourcePath);
        // and set token on request which is required by Leshan's message observer handling the response
        final var token = tokenGenerator.createToken(Scope.SHORT_TERM);
        observeRequest.getValue().setToken(token);

        // and let device accept the observe request
        final var observeResponse = new Response(ResponseCode.CONTENT);
        observeResponse.getOptions().setObserve(1);
        observeResponse.getOptions().setContentFormat(MediaTypeRegistry.APPLICATION_VND_OMA_LWM2M_TLV);
        observeRequest.getValue().getMessageObservers()
            .forEach(obs -> {
                // required to prevent NPE when processing response
                obs.onReadyToSend();
                obs.onResponse(observeResponse);
            });
        // add observation to Californium CoapEndpoint's ObservationStore
        // so that it can be found (and canceled) when removing the LwM2M registration
        // in a "non-mocked" environment this would be done automatically under the hood
        // by the CoapEndpoint implementation
        observationStore.putIfAbsent(token, new Observation(
                observeRequest.getValue(),
                new AddressEndpointContext(clientAddress)));
        assertThat(observationStore.getObservations(registrationId)).anyMatch(obs -> obs.getPath().getObjectId() == path.getObjectId());
        return token;
    }

    /**
     * Verifies that the store sends an empty notification downstream that corresponds to the
     * binding mode used by the device. Also verifies that the resources configured for the
     * device are getting observed.
     *
     * @param endpoint The endpoint name that the device uses when registering.
     * @param bindingMode The binding mode that the device uses when registering.
     * @param lifetime The lifetime that the device uses when registering.
     * @param lwm2mVersion The version of the LwM2M enabler that the device uses when registering.
     * @param notificationEndpoint The type of endpoint to use for forwarding notifications.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @CsvSource(value = { "test-ep,U,84600,v1.0,TELEMETRY", "test-ep,UQ,300,v1.0.2,EVENT"})
    public void testAddRegistrationSucceeds(
            final String endpoint,
            final BindingMode bindingMode,
            final Integer lifetime,
            final String lwm2mVersion,
            final EndpointType notificationEndpoint,
            final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream consumer
        givenATelemetrySenderForAnyTenant();
        givenAnEventSenderForAnyTenant();
        store.setResourcesToObserve(Map.of("/3/0", notificationEndpoint));

        // WHEN a device registers the standard Device object
        final var registrationId = "reg-id";
        final var registration = newRegistration(registrationId, endpoint, lwm2mVersion, lifetime, bindingMode);

        store.addRegistration(registration, NoopSpan.INSTANCE.context())
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the store sends an empty notification downstream corresponding
                    // to the binding mode,
                    verify(adapter).sendTtdEvent(
                            eq("tenant"),
                            eq("device"),
                            isNull(),
                            eq(BindingMode.U == bindingMode ? lifetime : 20),
                            any());
                });
                // opens a command consumer for the device
                ctx.verify(() -> {
                    verify(commandConsumerFactory).createCommandConsumer(
                            eq("tenant"),
                            eq("device"),
                            VertxMockSupport.anyHandler(),
                            isNull(),
                            any());
                });
                ctx.verify(() -> {
                    // and establishes an observe relation for the Device object
                    assertObserveRelationEstablished(registrationId, "3/0");
                    // THEN the store forwards the observe response downstream
                    if (notificationEndpoint == EndpointType.TELEMETRY) {
                        assertTelemetryMessageHasBeenSentDownstream(
                                QoS.AT_MOST_ONCE,
                                "tenant",
                                "device",
                                MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_VND_OMA_LWM2M_TLV));
                    }
                    if (notificationEndpoint == EndpointType.EVENT) {
                        assertEventHasBeenSentDownstream(
                                "tenant",
                                "device",
                                MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_VND_OMA_LWM2M_TLV));
                    }
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the store sends an empty notification downstream that corresponds to the
     * binding mode used by the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateRegistrationSucceeds(final VertxTestContext ctx) {

        // GIVEN a registered device
        givenATelemetrySenderForAnyTenant();
        final var registrationId = "reg-id";
        final var originalRegistration = newRegistration(registrationId, "device-ep", "v1.0.2", 84600, BindingMode.U);

        store.addRegistration(originalRegistration, null)
            .compose(ok -> {
                ctx.verify(() -> {
                    assertObserveRelationEstablished(registrationId, "3/0");
                });
                // WHEN the device updates its registration with a new lifetime
                final var registrationUpdate = new RegistrationUpdate(
                        registrationId,
                        identity,
                        300L,
                        null,
                        null,
                        null,
                        null);
                return store.updateRegistration(registrationUpdate, null);
            })
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the device's command consumer is being kept open
                    verify(commandConsumer, never()).close(any());
                    // and an empty notification is being sent downstream
                    // with a TTD reflecting the updated lifetime
                    verify(adapter).sendTtdEvent(
                            eq("tenant"),
                            eq("device"),
                            isNull(),
                            eq(300),
                            any());
                    // and the observation of the Device object is kept alive
                    verify(secureEndpoint, never()).cancelObservation(any(Token.class));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the store sends an empty notification downstream with a TTD of 0.
     * Also verifies that the command consumer for the device is being closed and that observations for
     * the device are being canceled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveRegistrationSucceeds(final VertxTestContext ctx) {

        // GIVEN a registered device
        givenATelemetrySenderForAnyTenant();
        final var registrationId = "reg-id";
        final var originalRegistration = newRegistration(registrationId, "device-ep", "v1.0.2", 84600, BindingMode.U);

        store.addRegistration(originalRegistration, null)
            .map(ok -> {
                final AtomicReference<Token> observationToken = new AtomicReference<>();
                ctx.verify(() -> {
                    observationToken.set(assertObserveRelationEstablished(registrationId, "3/0"));
                });
                // WHEN the device deregisters
                store.removeRegistration(registrationId, null);
                return observationToken.get();
            })
            .onComplete(ctx.succeeding(observationToken -> {
                ctx.verify(() -> {
                    // THEN the device's command consumer is being closed
                    verify(commandConsumer).close(any());
                    // and an empty notification is being sent downstream
                    verify(adapter).sendTtdEvent(
                            eq("tenant"),
                            eq("device"),
                            isNull(),
                            eq(0),
                            any());
                    // and the observation of the Device object is being canceled
                    verify(secureEndpoint).cancelObservation(observationToken);
                    // and no more observations are registered
                    assertThat(observationStore.getObservations(registrationId)).isEmpty();
                });
                ctx.completeNow();
            }));
    }
}
