/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.hono.service.tenant.AbstractCompleteTenantServiceTest;
import org.eclipse.hono.service.tenant.CompleteBaseTenantService;
import org.eclipse.hono.util.Constants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;


/**
 * Tests verifying behavior of {@link FileBasedTenantService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedTenantServiceTest extends AbstractCompleteTenantServiceTest {

    private static final String FILE_NAME = "/tenants.json";

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;
    private FileBasedTenantsConfigProperties props;
    private FileBasedTenantService svc;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        final Context ctx = mock(Context.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        props = new FileBasedTenantsConfigProperties();
        svc = new FileBasedTenantService();
        svc.setConfig(props);
        svc.init(vertx, ctx);
    }

    @Override
    public CompleteBaseTenantService getCompleteTenantService() {
        return svc;
    }

    /**
     * Verifies that the tenant service creates a file for persisting tenants
     * data if it does not exist yet during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartCreatesFile(final TestContext ctx) {

        // GIVEN a tenant service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN the file gets created
        startup.await();
        verify(fileSystem).createFile(eq(FILE_NAME), any(Handler.class));
    }

    /**
     * Verifies that the tenant service fails to start if it cannot create the file for
     * persisting tenants data during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final TestContext ctx) {

        // GIVEN a tenant service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure());
        svc.doStart(startupTracker);
    }

    /**
     * Verifies that the tenant service successfully starts up even if
     * the file to read tenants from contains malformed JSON.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoresMalformedJson(final TestContext ctx) {

        // GIVEN a tenant service configured to read data from a file
        // that contains malformed JSON
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = mock(Buffer.class);
            when(data.getBytes()).thenReturn("NO JSON".getBytes(StandardCharsets.UTF_8));
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            // THEN startup succeeds
        }));
        svc.doStart(startupTracker);
    }

    /**
     * Verifies that tenants are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartLoadsTenants(final TestContext ctx) {

        // GIVEN a service configured with a file name
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN the service is started
        final Async startup = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        svc.doStart(startFuture);

        // THEN the credentials from the file are loaded
        startup.await();
        assertTenantExists(svc, Constants.DEFAULT_TENANT, ctx);
    }

    /**
     * Verifies that tenants are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartwithStartEmptyIgnoreTenants(final TestContext ctx) {

        // GIVEN a service configured with a file name
        props.setFilename(FILE_NAME);
        props.setStartEmpty(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Async startup = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        svc.doStart(startFuture);

        // THEN the credentials from the file are loaded
        startup.await();
        verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
    }

    /**
     * Verifies that the tenants file written by the registry when persisting the contents can
     * be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadTenantsCanReadOutputOfSaveToFile(final TestContext ctx) {

        // GIVEN a service configured to persist tenants to file
        // that contains some tenants
        props.setFilename(FILE_NAME);
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        final Async countDown = ctx.async();
        addTenant(Constants.DEFAULT_TENANT).compose(ok -> addTenant("OTHER_TENANT"))
            .setHandler(ctx.asyncAssertSuccess(ok -> countDown.complete()));
        countDown.await();

        // WHEN saving the content to the file and clearing the tenant registry
        final Async write = ctx.async();
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            write.complete();
            return null;
        }).when(fileSystem).writeFile(eq(FILE_NAME), any(Buffer.class), any(Handler.class));

        svc.saveToFile();
        write.await();
        final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
        verify(fileSystem).writeFile(eq(FILE_NAME), buffer.capture(), any(Handler.class));
        svc.clear();
        assertTenantDoesNotExist(svc, Constants.DEFAULT_TENANT, ctx);

        // THEN the tenants can be loaded back in from the file
        final Async read = ctx.async();
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(buffer.getValue()));
            read.complete();
            return null;
        }).when(fileSystem).readFile(eq(FILE_NAME), any(Handler.class));
        svc.loadTenantData();
        read.await();
        assertTenantExists(svc, Constants.DEFAULT_TENANT, ctx);
        assertTenantExists(svc, "OTHER_TENANT", ctx);
    }

    /**
     * Verifies that a tenant can be added with <em>modificationEnabled</em>
     * set to {@code false}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to add a new tenant
        svc.add("fancy-new-tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            // THEN the request succeeds
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
        }));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing a tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveTenantsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to update the tenant
        svc.remove("tenant", ctx.asyncAssertSuccess(s -> {
            // THEN the update fails
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
        }));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to update the tenant
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            // THEN the update fails
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
        }));
    }

}
