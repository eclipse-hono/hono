/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonHelper;


/**
 * Tests verifying behavior of {@link AbstractProtocolAdapterBase}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractProtocolAdapterBaseTest {

    /**
     * Time out each test after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private static final String ADAPTER_NAME = "abstract-adapter";

    private ProtocolAdapterProperties properties;
    private AbstractProtocolAdapterBase<ProtocolAdapterProperties> adapter;
    private RegistrationClient registrationClient;
    private HonoClient tenantService;
    private HonoClient registrationService;
    private HonoClient credentialsService;
    private HonoClient messagingService;

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

        properties = new ProtocolAdapterProperties();
        adapter = newProtocolAdapter(properties);
        adapter.setTenantServiceClient(tenantService);
        adapter.setRegistrationServiceClient(registrationService);
        adapter.setCredentialsServiceClient(credentialsService);
        adapter.setHonoMessagingClient(messagingService);
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
     * startup and invokes the <em>doStart</em> method.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartInternalConnectsToServices(final TestContext ctx) {

        // GIVEN an adapter configured with service clients
        // that can connect to the corresponding services
        final Handler<Void> startupHandler = mock(Handler.class);
        adapter = newProtocolAdapter(properties, "test", startupHandler);
        adapter.setCredentialsServiceClient(credentialsService);
        adapter.setHonoMessagingClient(messagingService);
        adapter.setRegistrationServiceClient(registrationService);
        adapter.setTenantServiceClient(tenantService);

        // WHEN starting the adapter
        adapter.startInternal().setHandler(ctx.asyncAssertSuccess(ok -> {
            // THEN the service clients have connected
            verify(tenantService).connect(any(Handler.class));
            verify(registrationService).connect(any(Handler.class));
            verify(messagingService).connect(any(Handler.class));
            verify(credentialsService).connect(any(Handler.class));
            verify(startupHandler).handle(null);
        }));
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

        // WHEN an assertion for the device is retrieved
        adapter.getRegistrationAssertion("tenant", "device", null).setHandler(ctx.asyncAssertSuccess(result -> {
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

        // WHEN an assertion for a non-existing device is retrieved
        adapter.getRegistrationAssertion("tenant", "non-existent", null).setHandler(ctx.asyncAssertFailure(t -> {
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
                new Device("tenant B", "gateway")).setHandler(ctx.asyncAssertFailure(t -> {
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
        };
        result.setConfig(props);
        return result;
    }

    private static JsonObject newRegistrationAssertionResult(final String token) {
        return newRegistrationAssertionResult(token, null);
    }

    private static JsonObject newRegistrationAssertionResult(final String token, final String defaultContentType) {

        JsonObject result = new JsonObject()
                .put(RegistrationConstants.FIELD_ASSERTION, token);
        if (defaultContentType != null) {
            result.put(RegistrationConstants.FIELD_DEFAULTS, new JsonObject()
                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, defaultContentType));
        }
        return result;
    }
}
