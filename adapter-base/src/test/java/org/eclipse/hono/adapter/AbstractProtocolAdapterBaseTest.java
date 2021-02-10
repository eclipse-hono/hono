/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.ConnectionDurationExceededException;
import org.eclipse.hono.service.DataVolumeExceededException;
import org.eclipse.hono.service.TenantConnectionsExceededException;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.monitoring.HonoEventConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TelemetryExecutionContext;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link AbstractProtocolAdapterBase}.
 *
 */
@ExtendWith(VertxExtension.class)
public class AbstractProtocolAdapterBaseTest {

    private static final String ADAPTER_NAME = "abstract-adapter";

    private Vertx vertx;
    private Context context;
    private ProtocolAdapterProperties properties;
    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> adapter;
    private TenantClient tenantClient;
    private DeviceRegistrationClient registrationClient;
    private CredentialsClient credentialsClient;
    private TelemetrySender telemetrySender;
    private EventSender eventSender;
    private CommandConsumerFactory commandConsumerFactory;
    private CommandResponseSender commandResponseSender;
    private CommandRouterClient commandRouterClient;
    private ConnectionEventProducer.Context connectionEventProducerContext;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setup() {

        tenantClient = mock(TenantClient.class);
        when(tenantClient.start()).thenReturn(Future.succeededFuture());

        registrationClient = mock(DeviceRegistrationClient.class);
        when(registrationClient.start()).thenReturn(Future.succeededFuture());

        credentialsClient = mock(CredentialsClient.class);
        when(credentialsClient.start()).thenReturn(Future.succeededFuture());

        telemetrySender = mock(TelemetrySender.class);
        when(telemetrySender.start()).thenReturn(Future.succeededFuture());
        eventSender = mock(EventSender.class);
        when(eventSender.start()).thenReturn(Future.succeededFuture());

        commandConsumerFactory = mock(CommandConsumerFactory.class);
        when(commandConsumerFactory.start()).thenReturn(Future.succeededFuture());

        commandResponseSender = mock(CommandResponseSender.class);
        when(commandResponseSender.start()).thenReturn(Future.succeededFuture());

        commandRouterClient = mock(CommandRouterClient.class);
        when(commandRouterClient.start()).thenReturn(Future.succeededFuture());

        connectionEventProducerContext = mock(ConnectionEventProducer.Context.class);
        when(connectionEventProducerContext.getMessageSenderClient()).thenReturn(eventSender);
        when(connectionEventProducerContext.getTenantClient()).thenReturn(tenantClient);

        properties = new ProtocolAdapterProperties();
        adapter = newProtocolAdapter(properties, ADAPTER_NAME);
        setCollaborators(adapter);

        vertx = mock(Vertx.class);
        VertxMockSupport.runTimersImmediately(vertx);
        context = mock(Context.class);
        adapter.init(vertx, context);
    }

    private void setCollaborators(final AbstractProtocolAdapterBase<?> adapter) {
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCommandResponseSender(commandResponseSender);
        adapter.setCommandRouterClient(commandRouterClient);
        adapter.setCredentialsClient(credentialsClient);
        adapter.setEventSender(eventSender);
        adapter.setRegistrationClient(registrationClient);
        adapter.setTelemetrySender(telemetrySender);
        adapter.setTenantClient(tenantClient);
    }

    private void givenAnAdapterConfiguredWithServiceClients(
            final Handler<Void> startupHandler) {

        adapter = newProtocolAdapter(
                properties,
                ADAPTER_NAME,
                startupHandler);
        setCollaborators(adapter);
    }

    /**
     * Verifies that an adapter that does not define a type name
     * cannot be started.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testStartInternalFailsIfNoTypeNameIsDefined(final VertxTestContext ctx) {

        // GIVEN an adapter that does not define a type name
        adapter = newProtocolAdapter(properties, null);
        setCollaborators(adapter);

        // WHEN starting the adapter
        adapter.startInternal().onComplete(ctx.failing(t -> ctx.verify(() -> {
            // THEN startup fails
            assertTrue(t instanceof IllegalStateException);
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the adapter connects to required services during
     * startup and invokes the <em>onCommandConnectionEstablished</em> and
     * <em>doStart</em> methods.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartInternalConnectsToServices(final VertxTestContext ctx) {

        // GIVEN an adapter configured with service clients
        // that can connect to the corresponding services
        final Handler<Void> startupHandler = mock(Handler.class);
        givenAnAdapterConfiguredWithServiceClients(startupHandler);
        // WHEN starting the adapter
        adapter.startInternal().onComplete(ctx.succeeding(ok -> ctx.verify(() -> {
            // THEN the service clients have connected
            verify(telemetrySender).start();
            verify(eventSender).start();
            verify(tenantClient).start();
            verify(registrationClient).start();
            verify(credentialsClient).start();
            verify(commandConsumerFactory).start();
            verify(commandResponseSender).start();
            verify(startupHandler).handle(null);

            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the adapter's name is set on a downstream message.
     */
    @Test
    public void testGetDownstreamPropertiesAddsStandardProperties() {

        final TelemetryExecutionContext context = mock(TelemetryExecutionContext.class);
        when(context.getDownstreamMessageProperties()).thenReturn(new HashMap<>());

        final Map<String, Object> props = adapter.getDownstreamMessageProperties(context);
        assertThat(props.get(MessageHelper.APP_PROPERTY_ORIG_ADAPTER), is(ADAPTER_NAME));
    }

    /**
     * Verifies that the adapter successfully retrieves a registration assertion
     * for an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionSucceedsForExistingDevice(final VertxTestContext ctx) {

        // GIVEN an adapter connected to a registration service
        final RegistrationAssertion assertionResult = newRegistrationAssertionResult("device");
        assertionResult.setAuthorizedGateways(List.of("gw"));
        when(registrationClient.assertRegistration(eq("tenant"), eq("device"), any(), any())).thenReturn(Future.succeededFuture(assertionResult));
        when(commandRouterClient.setLastKnownGatewayForDevice(anyString(), anyString(), anyString(), any())).thenReturn(Future.succeededFuture());

        // WHEN an assertion for the device is retrieved
        adapter.getRegistrationAssertion("tenant", "device", new Device("tenant", "gw"), mock(SpanContext.class))
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        // THEN the result contains the registration assertion
                        assertThat(result.getDeviceId(), is("device"));
                        // and the last known gateway has been updated
                        verify(commandRouterClient).setLastKnownGatewayForDevice(
                                eq("tenant"),
                                eq("device"),
                                eq("gw"),
                                any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter fails a request to get a registration assertion for
     * a non-existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionFailsWith404ForNonExistingDevice(final VertxTestContext ctx) {

        // GIVEN an adapter connected to a registration service
        when(registrationClient.assertRegistration(eq("tenant"), eq("non-existent"), any(), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        // WHEN an assertion for a non-existing device is retrieved
        adapter.getRegistrationAssertion("tenant", "non-existent", null, mock(SpanContext.class))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the request fails with a 404
                        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter fails a request to retrieve a token for a gateway that does not
     * belong to the same tenant as the device it wants to act on behalf of.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionFailsWith403ForNonMatchingTenant(final VertxTestContext ctx) {

        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN a gateway tries to get an assertion for a device from another tenant
        adapter.getRegistrationAssertion(
                "tenant A",
                "device",
                new Device("tenant B", "gateway"),
                mock(SpanContext.class)).onComplete(ctx.failing(t -> ctx.verify(() -> {
                    // THEN the request fails with a 403 Forbidden error
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ClientErrorException) t).getErrorCode());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the helper approves empty notification without payload.
     *
     */
    @Test
    public void testEmptyNotificationWithoutPayload() {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an empty event with an empty payload is approved, no error message must be returned
        final Buffer payload = null;
        final String contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;

        assertTrue(adapter.isPayloadOfIndicatedType(payload, contentType));
    }

    /**
     * Verifies that any empty notification with a payload is an error.
     *
     */
    @Test
    public void testEmptyNotificationWithPayload() {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an empty event with a non empty payload is approved, an error message must be returned
        final Buffer payload = Buffer.buffer("test");
        final String contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;

        assertFalse(adapter.isPayloadOfIndicatedType(payload, contentType));
    }

    /**
     * Verifies that any general message with a payload is approved.
     *
     */
    @Test
    public void testNonEmptyGeneralMessage() {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an non empty event with a non empty payload is approved, no error message must be returned
        final Buffer payload = Buffer.buffer("test");
        final String arbitraryContentType = "bum/lux";

        // arbitrary content-type needs non empty payload
        assertTrue(adapter.isPayloadOfIndicatedType(payload, arbitraryContentType));
    }

    /**
     * Verifies that any non empty message without a content type is approved.
     *
     */
    @Test
    public void testNonEmptyMessageWithoutContentType() {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an event without content type and a non empty payload is approved, no error message must be returned
        final Buffer payload = Buffer.buffer("test");

        // arbitrary content-type needs non empty payload
        assertTrue(adapter.isPayloadOfIndicatedType(payload, null));
    }

    /**
     * Verifies that any empty general message is an error.
     *
     */
    @Test
    public void testEmptyGeneralMessage() {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an event with content type and an empty payload is approved, an error message must be returned
        final Buffer payload = null;
        final String arbitraryContentType = "bum/lux";

        // arbitrary content-type needs non empty payload
        assertFalse(adapter.isPayloadOfIndicatedType(payload, arbitraryContentType));
    }

    /**
     * Verifies that the adapter uses an authenticated device's identity when validating an
     * address without a tenant ID and device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testValidateAddressUsesDeviceIdentityForAddressWithoutTenantAndDevice(final VertxTestContext ctx) {

        // WHEN an authenticated device publishes a message to an address that does not contain a tenant ID
        final Device authenticatedDevice = new Device("my-tenant", "4711");
        final ResourceIdentifier address = ResourceIdentifier.fromString(TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.validateAddress(address, authenticatedDevice).onComplete(ctx.succeeding(r -> ctx.verify(() -> {
            // THEN the validated address contains the authenticated device's tenant and device ID
            assertEquals("my-tenant", r.getTenantId());
            assertEquals("4711", r.getResourceId());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the adapter uses an authenticated device's identity when validating an
     * address without a tenant ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testValidateAddressUsesDeviceIdentityForAddressWithoutTenant(final VertxTestContext ctx) {

        // WHEN an authenticated device publishes a message to an address that does not contain a tenant ID
        final Device authenticatedDevice = new Device("my-tenant", "4711");
        final ResourceIdentifier address = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, "", "4712");
        adapter.validateAddress(address, authenticatedDevice).onComplete(ctx.succeeding(r -> ctx.verify(() -> {
            // THEN the validated address contains the authenticated device's tenant and device ID
            assertEquals("my-tenant", r.getTenantId());
            assertEquals("4712", r.getResourceId());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the connection limit check fails if the maximum number of connections
     * for a tenant have been reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCheckConnectionLimitFailsIfConnectionLimitIsReached(final VertxTestContext ctx) {

        // GIVEN a tenant for which the maximum number of connections has been reached
        final TenantObject tenant = TenantObject.from("my-tenant", Boolean.TRUE);
        final ResourceLimitChecks checks = mock(ResourceLimitChecks.class);
        when(checks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        when(checks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(checks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        adapter.setResourceLimitChecks(checks);

        // WHEN a device tries to connect
        adapter.checkConnectionLimit(tenant, mock(SpanContext.class)).onComplete(ctx.failing(t -> {
            // THEN the connection limit check fails
            ctx.verify(() -> assertThat(t, instanceOf(TenantConnectionsExceededException.class)));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the message limit check fails, if the maximum number of messages
     * for a tenant have been reached. Also verifies that the payload size of the incoming
     * message is calculated based on the configured minimum message size.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCheckMessageLimitFailsIfMessageLimitIsReached(final VertxTestContext ctx) {
        // GIVEN a tenant with a minimum message size of 4kb configured 
        final TenantObject tenant = TenantObject.from("my-tenant", Boolean.TRUE);
        tenant.setMinimumMessageSize(4096);

        final ArgumentCaptor<Long> payloadSizeCaptor = ArgumentCaptor.forClass(Long.class);

        // And for that tenant, the maximum messages limit has been already reached
        final ResourceLimitChecks checks = mock(ResourceLimitChecks.class);
        when(checks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(checks.isMessageLimitReached(any(TenantObject.class), payloadSizeCaptor.capture(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        when(checks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        adapter.setResourceLimitChecks(checks);

        // WHEN a device sends a message with a payload size of 5000 bytes
        adapter.checkMessageLimit(tenant, 5000, mock(SpanContext.class)).onComplete(ctx.failing(t -> {
            // THEN the payload size used for the message limit checks is calculated based on the minimum message size.
            // In this case it should be 8kb
            assertEquals(8 * 1024, payloadSizeCaptor.getValue());
            // THEN the message limit check fails
            ctx.verify(
                    () -> assertThat(((ClientErrorException) t).getErrorCode(), is(HttpUtils.HTTP_TOO_MANY_REQUESTS)));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the connection limit check fails if the tenant's message limit has been reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCheckConnectionLimitFailsIfMessageLimitIsReached(final VertxTestContext ctx) {

        // GIVEN a tenant for which the message limit has been reached
        final TenantObject tenant = TenantObject.from("my-tenant", Boolean.TRUE);
        final ResourceLimitChecks checks = mock(ResourceLimitChecks.class);
        when(checks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(checks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        when(checks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        adapter.setResourceLimitChecks(checks);

        // WHEN a device tries to connect
        adapter.checkConnectionLimit(tenant, mock(SpanContext.class)).onComplete(ctx.failing(t -> {
            // THEN the connection limit check fails
            ctx.verify(() -> assertThat(t, instanceOf(DataVolumeExceededException.class)));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the connection limit check fails if the tenant's connection duration limit has been reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCheckConnectionLimitFailsIfConnectionDurationLimitIsReached(final VertxTestContext ctx) {

        // GIVEN a tenant for which the connection duration limit has been reached
        final TenantObject tenant = TenantObject.from("my-tenant", Boolean.TRUE);
        final ResourceLimitChecks checks = mock(ResourceLimitChecks.class);
        when(checks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(checks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(checks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        adapter.setResourceLimitChecks(checks);

        // WHEN a device tries to connect
        adapter.checkConnectionLimit(tenant, mock(SpanContext.class)).onComplete(ctx.failing(t -> {
            // THEN the connection limit check fails
            ctx.verify(
                    () -> assertThat(t, instanceOf(ConnectionDurationExceededException.class)));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the connection duration check fails if the tenant's connection duration
     * limit has been already reached.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCheckConnectionDurationLimit(final VertxTestContext ctx) {

        //Given a tenant for which the maximum connection duration usage already exceeds the limit.
        final TenantObject tenant = TenantObject.from("tenant", Boolean.TRUE);
        final ResourceLimitChecks checks = mock(ResourceLimitChecks.class);
        when(checks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        adapter.setResourceLimitChecks(checks);

        //When a device tries to connect
        adapter.checkConnectionDurationLimit(tenant, mock(SpanContext.class))
                .onComplete(ctx.failing(t -> {
                    //Then the connection duration limit check fails
                    ctx.verify(() -> assertThat(t, instanceOf(ConnectionDurationExceededException.class)));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the (default) ConnectionEvent API configured for a protocol adapter
     * forwards the message to downstream applications.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectionEventGetsSent(final VertxTestContext ctx) {

        // GIVEN a protocol adapter configured to send connection events
        final ConnectionEventProducer connectionEventProducer = new HonoEventConnectionEventProducer();
        adapter.setConnectionEventProducer(connectionEventProducer);
        when(eventSender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(),
                any(),
                any(),
                any())).thenReturn(Future.succeededFuture());

        // WHEN a device connects to such an adapter
        final Device authenticatedDevice = new Device(Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenantObject = TenantObject.from(Constants.DEFAULT_TENANT, true);
        when(tenantClient.get(eq(Constants.DEFAULT_TENANT), any())).thenReturn(Future.succeededFuture(tenantObject));

        // THEN the adapter forwards the connection event message downstream
        adapter.sendConnectedEvent("remote-id", authenticatedDevice)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    verify(eventSender).sendEvent(
                            eq(tenantObject),
                            argThat(assertion -> assertion.getDeviceId().equals("4711")),
                            eq(EventConstants.EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE),
                            any(Buffer.class),
                            any(),
                            any());
                });
                ctx.completeNow();
            }));
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(
            final ProtocolAdapterProperties props,
            final String typeName) {
        return newProtocolAdapter(props, typeName, start -> {});
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(
            final ProtocolAdapterProperties props,
            final String typeName,
            final Handler<Void> startupHandler) {

        final AbstractProtocolAdapterBase<ProtocolAdapterProperties> result = new AbstractProtocolAdapterBase<>() {

            @Override
            public String getTypeName() {
                return typeName;
            }

            @Override
            public int getPortDefaultValue() {
                return 0;
            }

            @Override
            public int getInsecurePortDefaultValue() {
                return 0;
            }

            @Override
            protected int getActualPort() {
                return 0;
            }

            @Override
            protected int getActualInsecurePort() {
                return 0;
            }

            @Override
            protected void doStart(final Promise<Void> startPromise) {
                startupHandler.handle(null);
                startPromise.complete();
            }
        };
        result.setConfig(props);
        result.init(vertx, context);
        return result;
    }

    private static RegistrationAssertion newRegistrationAssertionResult(final String deviceId) {
        return newRegistrationAssertionResult(deviceId, null);
    }

    private static RegistrationAssertion newRegistrationAssertionResult(
            final String deviceId,
            final String defaultContentType) {

        final RegistrationAssertion result = new RegistrationAssertion(deviceId);
        Optional.ofNullable(defaultContentType)
            .ifPresent(ct -> result.setDefaults(Map.of(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, ct)));
        return result;
    }
}
