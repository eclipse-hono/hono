/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonHelper;


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
    private RegistrationClient registrationClient;
    private TenantClientFactory tenantService;
    private RegistrationClientFactory registrationClientFactory;
    private CredentialsClientFactory credentialsClientFactory;
    private DownstreamSenderFactory downstreamSenderFactory;
    private CommandConsumerFactory commandConsumerFactory;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        tenantService = mock(TenantClientFactory.class);
        when(tenantService.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        registrationClient = mock(RegistrationClient.class);
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(registrationClient));

        credentialsClientFactory = mock(CredentialsClientFactory.class);
        when(credentialsClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        downstreamSenderFactory = mock(DownstreamSenderFactory.class);
        when(downstreamSenderFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        commandConsumerFactory = mock(CommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        properties = new ProtocolAdapterProperties();
        adapter = newProtocolAdapter(properties);
        adapter.setTenantClientFactory(tenantService);
        adapter.setRegistrationClientFactory(registrationClientFactory);
        adapter.setCredentialsClientFactory(credentialsClientFactory);
        adapter.setDownstreamSenderFactory(downstreamSenderFactory);
        adapter.setCommandConsumerFactory(commandConsumerFactory);

        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });
        context = mock(Context.class);
        adapter.init(vertx, context);
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
        adapter.setTenantClientFactory(tenantService);
        adapter.setRegistrationClientFactory(registrationClientFactory);
        adapter.setCredentialsClientFactory(credentialsClientFactory);
        adapter.setDownstreamSenderFactory(downstreamSenderFactory);
        adapter.setCommandConsumerFactory(commandConsumerFactory);

        // WHEN starting the adapter
        adapter.startInternal().setHandler(ctx.failing(t -> ctx.verify(() -> {
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
        final Handler<Void> commandConnectionHandler = mock(Handler.class);
        final Handler<Void> commandConnectionLostHandler = mock(Handler.class);
        givenAnAdapterConfiguredWithServiceClients(startupHandler, commandConnectionHandler, commandConnectionLostHandler);
        // WHEN starting the adapter
        adapter.startInternal().setHandler(ctx.succeeding(ok -> ctx.verify(() -> {
            // THEN the service clients have connected
            verify(tenantService).connect();
            verify(registrationClientFactory).connect();
            verify(downstreamSenderFactory).connect();
            verify(credentialsClientFactory).connect();
            verify(commandConsumerFactory).connect();
            verify(commandConsumerFactory).addDisconnectListener(any(DisconnectListener.class));
            verify(commandConsumerFactory).addReconnectListener(any(ReconnectListener.class));
            verify(startupHandler).handle(null);
            // and the establishment of the command consumer factory's connection
            // is signaled
            verify(commandConnectionHandler).handle(null);
            verify(commandConnectionLostHandler, never()).handle(null);

            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>onCommandConnectionLost</em> and
     * <em>onCommandConnectionEstablished</em> hooks are invoked
     * when the command connection is re-established.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCallbacksInvokedOnReconnect(final VertxTestContext ctx) {

        // GIVEN an adapter connected to Hono services
        final Handler<Void> commandConnectionEstablishedHandler = mock(Handler.class);
        final Handler<Void> commandConnectionLostHandler = mock(Handler.class);
        givenAnAdapterConfiguredWithServiceClients(mock(Handler.class), commandConnectionEstablishedHandler, commandConnectionLostHandler);
        adapter.startInternal().setHandler(ctx.succeeding(ok -> ctx.verify(() -> {
            final ArgumentCaptor<DisconnectListener> disconnectHandlerCaptor = ArgumentCaptor.forClass(DisconnectListener.class);
            verify(commandConsumerFactory).addDisconnectListener(disconnectHandlerCaptor.capture());
            final ArgumentCaptor<ReconnectListener> reconnectHandlerCaptor = ArgumentCaptor.forClass(ReconnectListener.class);
            verify(commandConsumerFactory).addReconnectListener(reconnectHandlerCaptor.capture());
            // WHEN the command connection is lost
            disconnectHandlerCaptor.getValue().onDisconnect(mock(HonoConnection.class));
            // THEN the onCommandConnectionLost hook is being invoked,
            verify(commandConnectionLostHandler).handle(null);
            // and when the connection is re-established
            reconnectHandlerCaptor.getValue().onReconnect(mock(HonoConnection.class));
            // the onCommandConnectionEstablished hook is being invoked
            verify(commandConnectionEstablishedHandler, times(2)).handle(null);

            ctx.completeNow();
        })));
    }

    private void givenAnAdapterConfiguredWithServiceClients(
            final Handler<Void> startupHandler,
            final Handler<Void> commandConnectionEstablishedHandler,
            final Handler<Void> commandConnectionLostHandler) {

        adapter = newProtocolAdapter(properties, "test", startupHandler,
                commandConnectionEstablishedHandler, commandConnectionLostHandler);
        adapter.setCredentialsClientFactory(credentialsClientFactory);
        adapter.setDownstreamSenderFactory(downstreamSenderFactory);
        adapter.setRegistrationClientFactory(registrationClientFactory);
        adapter.setTenantClientFactory(tenantService);
        adapter.setCommandConsumerFactory(commandConsumerFactory);
    }

    /**
     * Verifies that the adapter's name is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsStandardProperties() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        adapter.addProperties(message, target, "/status", tenant, newRegistrationAssertionResult(), 15);

        assertThat(
                MessageHelper.getApplicationProperty(
                        message.getApplicationProperties(),
                        MessageHelper.APP_PROPERTY_ORIG_ADDRESS,
                        String.class),
                is("/status"));
        assertThat(
                MessageHelper.getApplicationProperty(
                        message.getApplicationProperties(),
                        MessageHelper.APP_PROPERTY_ORIG_ADAPTER,
                        String.class),
                is(ADAPTER_NAME));
        assertThat(MessageHelper.getDeviceId(message), is("4711"));
        assertThat(MessageHelper.getTimeUntilDisconnect(message), is(15));
        assertThat(message.getTtl(), is(0L));
    }

    /**
     * Verifies that the registered default content type is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsDefaultContentType() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        adapter.addProperties(message, target, null, tenant, newRegistrationAssertionResult("application/hono"), null);

        assertThat(message.getContentType(), is("application/hono"));
    }

    /**
     * Verifies that the registered default content type is not set on a downstream message
     * that already contains a content type.
     */
    @Test
    public void testAddPropertiesDoesNotAddDefaultContentType() {

        final Message message = ProtonHelper.message();
        message.setContentType("application/existing");
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        adapter.addProperties(message, target, null, tenant, newRegistrationAssertionResult("application/hono"), null);

        assertThat(message.getContentType(), is("application/existing"));
    }

    /**
     * Verifies that the fall back content type is set on a downstream message
     * if no default has been configured for the device.
     */
    @Test
    public void testAddPropertiesAddsFallbackContentType() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);

        adapter.addProperties(message, target, null, tenant, newRegistrationAssertionResult(), null);

        assertThat(message.getContentType(), is(AbstractProtocolAdapterBase.CONTENT_TYPE_OCTET_STREAM));
    }

    /**
     * Verifies that default properties configured at the tenant and/or device level
     * are set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsCustomProperties() {

        final Message message = ProtonHelper.message();
        final ResourceIdentifier target = ResourceIdentifier.from(EventConstants.EVENT_ENDPOINT, Constants.DEFAULT_TENANT, "4711");
        final TenantObject tenant = TenantObject.from(Constants.DEFAULT_TENANT, true);
        tenant.setDefaults(new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 60).put("custom-tenant", "foo"));
        final JsonObject assertion = newRegistrationAssertionResult();
        assertion.put(
                RegistrationConstants.FIELD_PAYLOAD_DEFAULTS,
                new JsonObject().put(MessageHelper.SYS_HEADER_PROPERTY_TTL, 30).put("custom-device", true));

        adapter.addProperties(message, target, null, tenant, assertion, null);

        assertThat(
                MessageHelper.getApplicationProperty(message.getApplicationProperties(), "custom-tenant", String.class),
                is("foo"));
        assertThat(
                MessageHelper.getApplicationProperty(message.getApplicationProperties(), "custom-device", Boolean.class),
                is(Boolean.TRUE));
        assertThat(message.getTtl(), is(30L));
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
        final JsonObject assertionResult = newRegistrationAssertionResult();
        when(registrationClient.assertRegistration(eq("device"), any())).thenReturn(Future.succeededFuture(assertionResult));
        when(registrationClient.assertRegistration(eq("device"), any(), any())).thenReturn(Future.succeededFuture(assertionResult));

        final Checkpoint assertion = ctx.checkpoint();
        // WHEN an assertion for the device is retrieved
        adapter.getRegistrationAssertion("tenant", "device", null)
                .setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the result contains the registration assertion
            assertEquals(assertionResult, result);
            assertion.flag();
        })));
        adapter.getRegistrationAssertion("tenant", "device", null, mock(SpanContext.class))
                .setHandler(ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the result contains the registration assertion
            assertEquals(assertionResult, result);
            assertion.flag();
        })));
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
        when(registrationClient.assertRegistration(eq("non-existent"), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        when(registrationClient.assertRegistration(eq("non-existent"), any(), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        final Checkpoint assertion = ctx.checkpoint();
        // WHEN an assertion for a non-existing device is retrieved
        adapter.getRegistrationAssertion("tenant", "non-existent", null)
                .setHandler(ctx.failing(t -> ctx.verify(() -> {
            // THEN the request fails with a 404
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
            assertion.flag();
        })));
        adapter.getRegistrationAssertion("tenant", "non-existent", null, mock(SpanContext.class))
                .setHandler(ctx.failing(t -> ctx.verify(() -> {
            // THEN the request fails with a 404
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
            assertion.flag();
        })));
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
                mock(SpanContext.class)).setHandler(ctx.failing(t -> ctx.verify(() -> {
                    // THEN the request fails with a 403 Forbidden error
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ClientErrorException) t).getErrorCode());
                    ctx.completeNow();
                })));
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(final ProtocolAdapterProperties props) {

        return newProtocolAdapter(props, ADAPTER_NAME);
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(final ProtocolAdapterProperties props, final String typeName) {
        return newProtocolAdapter(props, typeName, start -> {});
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(
            final ProtocolAdapterProperties props,
            final String typeName,
            final Handler<Void> startupHandler) {
        return newProtocolAdapter(props, typeName, startupHandler, null, null);
    }

    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> newProtocolAdapter(
            final ProtocolAdapterProperties props,
            final String typeName,
            final Handler<Void> startupHandler,
            final Handler<Void> commandConnectionEstablishedHandler,
            final Handler<Void> commandConnectionLostHandler) {

        final AbstractProtocolAdapterBase<ProtocolAdapterProperties> result = new AbstractProtocolAdapterBase<ProtocolAdapterProperties>() {

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
            protected void doStart(final Future<Void> startFuture) {
                startupHandler.handle(null);
                startFuture.complete();
            }

            @Override
            protected void onCommandConnectionEstablished(final HonoConnection connectedClient) {
                if (commandConnectionEstablishedHandler != null) {
                    commandConnectionEstablishedHandler.handle(null);
                }
            }

            @Override
            protected void onCommandConnectionLost(final HonoConnection commandConnection) {
                if (commandConnectionLostHandler != null) {
                    commandConnectionLostHandler.handle(null);
                }
            }
        };
        result.setConfig(props);
        result.init(vertx, context);
        return result;
    }

    private static JsonObject newRegistrationAssertionResult() {
        return newRegistrationAssertionResult(null);
    }

    private static JsonObject newRegistrationAssertionResult(final String defaultContentType) {

        final JsonObject result = new JsonObject();
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
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
     * address without a tenant ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testValidateAddressUsesDeviceIdentityForAddressWithoutTenant(final VertxTestContext ctx) {

        // WHEN an authenticated device publishes a message to an address that does not contain a tenant ID
        final Device authenticatedDevice = new Device("my-tenant", "4711");
        final ResourceIdentifier address = ResourceIdentifier.fromString(TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.validateAddress(address, authenticatedDevice).setHandler(ctx.succeeding(r -> ctx.verify(() -> {
            // THEN the validated address contains the authenticated device's tenant and device ID
            assertEquals("my-tenant", r.getTenantId());
            assertEquals("4711", r.getResourceId());
            ctx.completeNow();
        })));
    }
}
