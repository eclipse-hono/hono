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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
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
import org.eclipse.hono.client.CommandConnection;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;


/**
 * Tests verifying behavior of {@link AbstractProtocolAdapterBase}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractProtocolAdapterBaseTest {

    private static final String ADAPTER_NAME = "abstract-adapter";

    /**
     * Time out each test after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private Vertx vertx;
    private Context context;
    private ProtocolAdapterProperties properties;
    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> adapter;
    private RegistrationClient registrationClient;
    private HonoClient tenantService;
    private HonoClient registrationService;
    private HonoClient credentialsService;
    private HonoClient messagingService;
    private CommandConnection commandConnection;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup() {

        tenantService = mock(HonoClient.class);
        when(tenantService.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantService));

        registrationService = mock(HonoClient.class);
        when(registrationService.connect(any(Handler.class))).thenReturn(Future.succeededFuture(registrationService));

        registrationClient = mock(RegistrationClient.class);
        when(registrationService.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(registrationClient));

        credentialsService = mock(HonoClient.class);
        when(credentialsService.connect(any(Handler.class))).thenReturn(Future.succeededFuture(credentialsService));

        messagingService = mock(HonoClient.class);
        when(messagingService.connect(any(Handler.class))).thenReturn(Future.succeededFuture(messagingService));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));

        properties = new ProtocolAdapterProperties();
        adapter = newProtocolAdapter(properties);
        adapter.setTenantServiceClient(tenantService);
        adapter.setRegistrationServiceClient(registrationService);
        adapter.setCredentialsServiceClient(credentialsService);
        adapter.setHonoMessagingClient(messagingService);
        adapter.setCommandConnection(commandConnection);

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
    public void testStartInternalFailsIfNoTypeNameIsDefined(final TestContext ctx) {

        // GIVEN an adapter that does not define a type name
        adapter = newProtocolAdapter(properties, null);
        adapter.setRegistrationServiceClient(mock(HonoClient.class));

        // WHEN starting the adapter
        // THEN startup fails
        adapter.startInternal().setHandler(ctx.asyncAssertFailure());
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
    public void testStartInternalConnectsToServices(final TestContext ctx) {

        // GIVEN an adapter configured with service clients
        // that can connect to the corresponding services
        final Handler<Void> startupHandler = mock(Handler.class);
        final Handler<Void> commandConnectionHandler = mock(Handler.class);
        final Handler<Void> commandConnectionLostHandler = mock(Handler.class);
        givenAnAdapterConfiguredWithServiceClients(startupHandler, commandConnectionHandler, commandConnectionLostHandler);

        // WHEN starting the adapter
        adapter.startInternal().setHandler(ctx.asyncAssertSuccess(ok -> {
            // THEN the service clients have connected
            verify(tenantService).connect(any(Handler.class));
            verify(registrationService).connect(any(Handler.class));
            verify(messagingService).connect(any(Handler.class));
            verify(credentialsService).connect(any(Handler.class));
            verify(commandConnection).connect(any(Handler.class));
            verify(startupHandler).handle(null);
            verify(commandConnectionHandler).handle(null);
            verify(commandConnectionLostHandler, never()).handle(null);
        }));
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
    public void testCallbacksInvokedOnReconnect(final TestContext ctx) {

        // GIVEN an adapter connected to Hono services
        final Handler<Void> commandConnectionEstablishedHandler = mock(Handler.class);
        final Handler<Void> commandConnectionLostHandler = mock(Handler.class);
        givenAnAdapterConfiguredWithServiceClients(mock(Handler.class), commandConnectionEstablishedHandler, commandConnectionLostHandler);
        adapter.startInternal().setHandler(ctx.asyncAssertSuccess(ok -> {
            final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
            verify(commandConnection).connect(disconnectHandlerCaptor.capture());
            // WHEN the command connection is lost
            disconnectHandlerCaptor.getValue().handle(mock(ProtonConnection.class));
            // THEN the onCommandConnectionLost hook is being invoked,
            verify(commandConnectionLostHandler).handle(null);
            // the connection is re-established and
            verify(commandConnection, times(2)).connect(any(Handler.class));
            // the onCommandConnectionEstablished hook is being invoked
            verify(commandConnectionEstablishedHandler, times(2)).handle(null);
        }));
    }

    private void givenAnAdapterConfiguredWithServiceClients(
            final Handler<Void> startupHandler,
            final Handler<Void> commandConnectionEstablishedHandler,
            final Handler<Void> commandConnectionLostHandler) {

        adapter = newProtocolAdapter(properties, "test", startupHandler,
                commandConnectionEstablishedHandler, commandConnectionLostHandler);
        adapter.setCredentialsServiceClient(credentialsService);
        adapter.setHonoMessagingClient(messagingService);
        adapter.setRegistrationServiceClient(registrationService);
        adapter.setTenantServiceClient(tenantService);
        adapter.setCommandConnection(commandConnection);
    }

    /**
     * Verifies that the registration assertion is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsRegistrationAssertion() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
    }

    /**
     * Verifies that the registration assertion is not set on a downstream message
     * if the downstream peer does not require it.
     */
    @Test
    public void testAddPropertiesOmitsRegistrationAssertion() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token"), false);
        assertNull(MessageHelper.getRegistrationAssertion(message));
    }

    /**
     * Verifies that the adapter's name is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsAdapterName() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token"));
        assertThat(
                MessageHelper.getApplicationProperty(
                        message.getApplicationProperties(),
                        MessageHelper.APP_PROPERTY_ORIG_ADAPTER,
                        String.class),
                is(ADAPTER_NAME));
    }

    /**
     * Verifies that the registered default content type is set on a downstream message.
     */
    @Test
    public void testAddPropertiesAddsDefaultContentType() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token", "application/hono"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
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
        adapter.addProperties(message, newRegistrationAssertionResult("token", "application/hono"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
        assertThat(message.getContentType(), is("application/existing"));
    }

    /**
     * Verifies that the fall back content type is set on a downstream message
     * if no default has been configured for the device.
     */
    @Test
    public void testAddPropertiesAddsFallbackContentType() {

        final Message message = ProtonHelper.message();
        adapter.addProperties(message, newRegistrationAssertionResult("token"));
        assertThat(MessageHelper.getRegistrationAssertion(message), is("token"));
        assertThat(message.getContentType(), is(AbstractProtocolAdapterBase.CONTENT_TYPE_OCTET_STREAM));
    }

    /**
     * Verifies that the adapter successfully retrieves a registration assertion
     * for an existing device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionSucceedsForExistingDevice(final TestContext ctx) {

        // GIVEN an adapter connected to a registration service
        final JsonObject assertionResult = newRegistrationAssertionResult("token");
        when(registrationClient.assertRegistration(eq("device"), any())).thenReturn(Future.succeededFuture(assertionResult));
        when(registrationClient.assertRegistration(eq("device"), any(), any())).thenReturn(Future.succeededFuture(assertionResult));

        // WHEN an assertion for the device is retrieved
        adapter.getRegistrationAssertion("tenant", "device", null).setHandler(ctx.asyncAssertSuccess(result -> {
            // THEN the result contains the registration assertion
            ctx.assertEquals(assertionResult, result);
        }));
        adapter.getRegistrationAssertion("tenant", "device", null, mock(SpanContext.class)).setHandler(ctx.asyncAssertSuccess(result -> {
            // THEN the result contains the registration assertion
            ctx.assertEquals(assertionResult, result);
        }));
    }

    /**
     * Verifies that the adapter fails a request to get a registration assertion for
     * a non-existing device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionFailsWith404ForNonExistingDevice(final TestContext ctx) {

        // GIVEN an adapter connected to a registration service
        when(registrationClient.assertRegistration(eq("non-existent"), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        when(registrationClient.assertRegistration(eq("non-existent"), any(), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        // WHEN an assertion for a non-existing device is retrieved
        adapter.getRegistrationAssertion("tenant", "non-existent", null).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the request fails with a 404
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
        adapter.getRegistrationAssertion("tenant", "non-existent", null, mock(SpanContext.class)).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the request fails with a 404
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the adapter fails a request to retrieve a token for a gateway that does not
     * belong to the same tenant as the device it wants to act on behalf of.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionFailsWith403ForNonMatchingTenant(final TestContext ctx) {

        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN a gateway tries to get an assertion for a device from another tenant
        adapter.getRegistrationAssertion(
                "tenant A",
                "device",
                new Device("tenant B", "gateway"),
                mock(SpanContext.class)).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the request fails with a 403 Forbidden error
                    ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ClientErrorException) t).getErrorCode());
                }));
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
            protected void onCommandConnectionEstablished(final HonoClient connectedClient) {
                if (commandConnectionEstablishedHandler != null) {
                    commandConnectionEstablishedHandler.handle(null);
                }
            }

            @Override
            protected void onCommandConnectionLost(final HonoClient commandConnection) {
                if (commandConnectionLostHandler != null) {
                    commandConnectionLostHandler.handle(null);
                }
            }
        };
        result.setConfig(props);
        result.init(vertx, context);
        return result;
    }

    private static JsonObject newRegistrationAssertionResult(final String token) {
        return newRegistrationAssertionResult(token, null);
    }

    private static JsonObject newRegistrationAssertionResult(final String token, final String defaultContentType) {

        final JsonObject result = new JsonObject()
                .put(RegistrationConstants.FIELD_ASSERTION, token);
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
    }

    /**
     * Verifies that the helper approves empty notification without payload.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testEmptyNotificationWithoutPayload(final TestContext ctx) {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an empty event with an empty payload is approved, no error message must be returned
        final Buffer payload = null;
        final String contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;

        ctx.assertTrue(adapter.isPayloadOfIndicatedType(payload, contentType));
    }

    /**
     * Verifies that any empty notification with a payload is an error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testEmptyNotificationWithPayload(final TestContext ctx) {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an empty event with a non empty payload is approved, an error message must be returned
        final Buffer payload = Buffer.buffer("test");
        final String contentType = EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION;

        ctx.assertFalse(adapter.isPayloadOfIndicatedType(payload, contentType));
    }

    /**
     * Verifies that any general message with a payload is approved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testNonEmptyGeneralMessage(final TestContext ctx) {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an non empty event with a non empty payload is approved, no error message must be returned
        final Buffer payload = Buffer.buffer("test");
        final String arbitraryContentType = "bum/lux";

        // arbitrary content-type needs non empty payload
        ctx.assertTrue(adapter.isPayloadOfIndicatedType(payload, arbitraryContentType));
    }

    /**
     * Verifies that any non empty message without a content type is approved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testNonEmptyMessageWithoutContentType(final TestContext ctx) {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an event without content type and a non empty payload is approved, no error message must be returned
        final Buffer payload = Buffer.buffer("test");

        // arbitrary content-type needs non empty payload
        ctx.assertTrue(adapter.isPayloadOfIndicatedType(payload, null));
    }

    /**
     * Verifies that any empty general message is an error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testEmptyGeneralMessage(final TestContext ctx) {
        // GIVEN an adapter
        adapter = newProtocolAdapter(properties, null);

        // WHEN an event with content type and an empty payload is approved, an error message must be returned
        final Buffer payload = null;
        final String arbitraryContentType = "bum/lux";

        // arbitrary content-type needs non empty payload
        ctx.assertFalse(adapter.isPayloadOfIndicatedType(payload, arbitraryContentType));
    }

    /**
     * Verifies that the adapter uses an authenticated device's identity when validating an
     * address without a tenant ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testValidateAddressUsesDeviceIdentityForAddressWithoutTenant(final TestContext ctx) {

        // WHEN an authenticated device publishes a message to an address that does not contain a tenant ID
        final Device authenticatedDevice = new Device("my-tenant", "4711");
        final ResourceIdentifier address = ResourceIdentifier.fromString(TelemetryConstants.TELEMETRY_ENDPOINT);
        adapter.validateAddress(address, authenticatedDevice).setHandler(ctx.asyncAssertSuccess(r -> {
            // THEN the validated address contains the authenticated device's tenant and device ID
            ctx.assertEquals("my-tenant", r.getTenantId());
            ctx.assertEquals("4711", r.getResourceId());
        }));
    }


}
