/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.deviceregistry;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link FileBasedCredentialsService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedCredentialsServiceTest {

    private static final String FILE_NAME = "/credentials.json";

    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;
    private FileBasedCredentialsConfigProperties props;
    private FileBasedCredentialsService svc;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        Context ctx = mock(Context.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        props = new FileBasedCredentialsConfigProperties();
        svc = new FileBasedCredentialsService();
        svc.setConfig(props);
        svc.init(vertx, ctx);
    }

    /**
     * Verifies that the credentials service creates a file for persisting credentials
     * data if it does not exist yet during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartCreatesFile(final TestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN the file gets created
        startup.await(2000);
        verify(fileSystem).createFile(eq(FILE_NAME), any(Handler.class));
    }

    /**
     * Verifies that the credentials service fails to start if it cannot create the file for
     * persisting credentials data during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final TestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN startup has failed
        startup.await(2000);
    }

    /**
     * Verifies that the credentials service successfully starts up even if
     * the file to read credentials from contains malformed JSON.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoresMalformedJson(final TestContext ctx) {

        // GIVEN a registration service configured to read data from a file
        // that contains malformed JSON
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = mock(Buffer.class);
            when(data.getBytes()).thenReturn("NO JSON".getBytes(StandardCharsets.UTF_8));
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN startup succeeds
        startup.await(2000);
    }

    /**
     * Verifies that credentials are successfully loaded from file during startup.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartLoadsCredentials(final TestContext ctx) {

        // GIVEN a service configured with a file name
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN the service is started
        Async startup = ctx.async();
        Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        svc.doStart(startFuture);

        // THEN the credentials from the file are loaded
        startup.await(2000);
        assertRegistered(svc, Constants.DEFAULT_TENANT, "sensor1", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, ctx);
    }

    /**
     * Verifies that the file written by the registry when persisting the registry's contents can
     * be loaded in again.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadCredentialsCanReadOutputOfSaveToFile(final TestContext ctx) {

        // GIVEN a service configured to persist credentials to file
        // that contains some credentials
        props.setFilename(FILE_NAME);
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        final Async add = ctx.async(2);
        final CredentialsObject hashedPassword = CredentialsObject.fromHashedPassword(
                "4700", "bumlux", "secret", "sha-512", null, null, null);
        final CredentialsObject psk = CredentialsObject.fromPresharedKey(
                "4711", "sensor1", "sharedkey".getBytes(StandardCharsets.UTF_8), null, null);
        svc.add(
                Constants.DEFAULT_TENANT,
                JsonObject.mapFrom(psk),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    add.countDown();
                }));
        svc.add(
                "OTHER_TENANT",
                JsonObject.mapFrom(hashedPassword),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    add.countDown();
                }));
        add.await(2000);

        // WHEN saving the registry content to the file and clearing the registry
        final Async write = ctx.async();
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            write.complete();
            return null;
        }).when(fileSystem).writeFile(eq(FILE_NAME), any(Buffer.class), any(Handler.class));

        svc.saveToFile();
        write.await(2000);
        ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
        verify(fileSystem).writeFile(eq(FILE_NAME), buffer.capture(), any(Handler.class));
        svc.clear();
        assertNotRegistered(svc, Constants.DEFAULT_PATH_SEPARATOR, "sensor1", CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, ctx);

        // THEN the credentials can be loaded back in from the file
        final Async read = ctx.async();
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(buffer.getValue()));
            read.complete();
            return null;
        }).when(fileSystem).readFile(eq(FILE_NAME), any(Handler.class));
        svc.loadCredentials();
        read.await(2000);
        assertRegistered(svc, Constants.DEFAULT_TENANT, "sensor1", CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, ctx);
        assertRegistered(svc, "OTHER_TENANT", "bumlux", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, ctx);

    }

    /**
     * Verifies that only one set of credentials can be registered for an auth-id and type (per tenant).
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddCredentialsRefusesDuplicateRegistration(final TestContext ctx) {

        register(svc, "tenant", "device", "myId", "myType", ctx);

        final JsonObject payload2 = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, "other-device")
                .put(CredentialsConstants.FIELD_AUTH_ID, "myId")
                .put(CredentialsConstants.FIELD_TYPE, "myType")
                .put(CredentialsConstants.FIELD_SECRETS, new JsonArray());
        final Async add = ctx.async();
        svc.add("tenant", payload2, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CONFLICT));
            add.complete();
        }));
        add.await(2000);
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingCredentials(final TestContext ctx) {

        final Async get = ctx.async();
        svc.get("tenant", "myType", "non-existing", ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
                    get.complete();
                }));
        get.await(2000);
    }

    /**
     * Verifies that the service returns existing credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForExistingCredentials(final TestContext ctx) {

        register(svc, "tenant", "device", "myId", "myType", ctx);

        final Async get = ctx.async();
        svc.get("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
                    assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
                    assertThat(s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID), is("myId"));
                    assertThat(s.getPayload().getString(CredentialsConstants.FIELD_TYPE), is("myType"));
                    get.complete();
                }));
        get.await(2000);
    }

    /**
     * Verifies that service returns existing credentials for proper client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForProperClientContext(final TestContext ctx) {
        JsonObject clientContext = new JsonObject()
            .put("client-id", "gateway-one");

        register(svc, "tenant", "device", "myId", "myType", clientContext, new JsonArray(), ctx);


        final Async get = ctx.async();
        svc.get("tenant", "myType", "myId", clientContext, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_OK));
            assertThat(s.getPayload().getString(CredentialsConstants.FIELD_AUTH_ID), is("myId"));
            assertThat(s.getPayload().getString(CredentialsConstants.FIELD_TYPE), is("myType"));
            get.complete();
        }));
        get.await(2000);
    }

    /**
     * Verifies that service returns 404 if a client provides wrong client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForWrongClientContext(final TestContext ctx) {
        JsonObject clientContext = new JsonObject()
            .put("client-id", "gateway-one");

        register(svc, "tenant", "device", "myId", "myType", clientContext, new JsonArray(), ctx);

        JsonObject testContext = new JsonObject()
            .put("client-id", "gateway-two");

        final Async get = ctx.async();
        svc.get("tenant", "myType", "myId", testContext, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            get.complete();
        }));
        get.await(2000);
    }

    /**
     * Verifies that the service removes credentials for a given auth-id and type.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByAuthIdAndTypeSucceeds(final TestContext ctx) {

        register(svc, "tenant", "device", "myId", "myType", ctx);

        final Async remove = ctx.async();
        svc.remove("tenant", "myType", "myId", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
            assertNotRegistered(svc, "tenant", "myId", "myType", ctx);
            remove.complete();
        }));
        remove.await(2000);
    }

    /**
     * Verifies that the service removes all credentials for a device but keeps credentials
     * of other devices.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsByDeviceSucceeds(final TestContext ctx) {

        register(svc, "tenant", "device", "myId", "myType", ctx);
        register(svc, "tenant", "device", "myOtherId", "myOtherType", ctx);
        register(svc, "tenant", "other-device", "thirdId", "myType", ctx);

        final Async remove = ctx.async();
        svc.removeAll("tenant", "device", ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_NO_CONTENT));
            assertNotRegistered(svc, "tenant", "myId", "myType", ctx);
            assertNotRegistered(svc, "tenant", "myOtherId", "myOtherType", ctx);
            assertRegistered(svc, "tenant", "thirdId", "myType", ctx);
            remove.complete();
        }));
        remove.await(2000);
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a registry containing a set of credentials
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);
        register(svc, "tenant", "device", "myId", "myType", ctx);

        // WHEN trying to update the credentials
        Async updateFailure = ctx.async();
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            updateFailure.complete();
        }));

        // THEN the update fails
        updateFailure.await(2000);
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing an existing entry.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveDeviceFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a registry containing a set of credentials
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);
        register(svc, "tenant", "device", "myId", "myType", ctx);

        // WHEN trying to remove the credentials
        Async removeFailure = ctx.async();
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            removeFailure.complete();
        }));

        // THEN the removal fails
        removeFailure.await(2000);
    }

    private static void assertRegistered(final CredentialsService svc, final String tenant, final String authId, final String type, final TestContext ctx) {
        Async registration = ctx.async();
        svc.get(tenant, type, authId, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_OK));
            registration.complete();
        }));
        registration.await(300);
    }

    private static void assertNotRegistered(final CredentialsService svc, final String tenant, final String authId, final String type, final TestContext ctx) {
        Async registration = ctx.async();
        svc.get(tenant, type, authId, ctx.asyncAssertSuccess(t -> {
            assertThat(t.getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
            registration.complete();
        }));
        registration.await(300);
    }

    private static void register(
            final CredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final TestContext ctx) {
        register(svc, tenant, deviceId, authId, type, null, new JsonArray(), ctx);
    }

    private static void register(
            final CredentialsService svc,
            final String tenant,
            final String deviceId,
            final String authId,
            final String type,
            final JsonObject clientContext,
            final JsonArray secrets,
            final TestContext ctx) {

        JsonObject data = new JsonObject()
                .put(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(CredentialsConstants.FIELD_TYPE, type)
                .put(CredentialsConstants.FIELD_SECRETS, secrets);

        if (clientContext != null) {
            data.mergeIn(clientContext);
        }


        Async registration = ctx.async();
        svc.add(tenant, data, ctx.asyncAssertSuccess(s -> {
            assertThat(s.getStatus(), is(HttpURLConnection.HTTP_CREATED));
            registration.complete();
        }));
        registration.await(300);
    }
}
