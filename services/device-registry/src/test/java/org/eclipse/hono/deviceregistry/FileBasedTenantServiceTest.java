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

package org.eclipse.hono.deviceregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.AbstractTenantServiceTest;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link FileBasedTenantService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class FileBasedTenantServiceTest extends AbstractTenantServiceTest {

    private static final String FILE_NAME = "/tenants.json";


    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;
    private FileBasedTenantsConfigProperties props;
    private FileBasedTenantService svc;

    /**
     * Sets up fixture.
     */
    @BeforeEach
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
    public TenantService getTenantService() {
        return svc;
    }

    @Override
    public TenantManagementService getTenantManagementService() {
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
    public void testDoStartCreatesFile(final VertxTestContext ctx) {

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
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(started -> ctx.verify(() -> {
            // THEN the file gets created
            verify(fileSystem).createFile(eq(FILE_NAME), any(Handler.class));
            ctx.completeNow();
        })));
        svc.start(startupTracker);
    }

    /**
     * Verifies that the tenant service fails to start if it cannot create the file for
     * persisting tenants data during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final VertxTestContext ctx) {

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
        startupTracker.setHandler(ctx.failing(started -> {
            ctx.completeNow();
        }));
        svc.start(startupTracker);
    }

    /**
     * Verifies that the tenant service successfully starts up even if
     * the file to read tenants from contains malformed JSON.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoresMalformedJson(final VertxTestContext ctx) {

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
        startupTracker.setHandler(ctx.completing(
            // THEN startup succeeds
        ));
        svc.start(startupTracker);
    }

    /**
     * Verifies that tenants are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartLoadsTenants(final VertxTestContext ctx) {

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
        final Future<Void> startFuture = Future.future();
        startFuture.compose(ok -> {
            // THEN the credentials from the file are loaded
            return assertTenantExists(svc, Constants.DEFAULT_TENANT);
        })
        .setHandler(ctx.completing());

        svc.start(startFuture);
    }

    /**
     * Verifies that tenants are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartWithStartEmptyIgnoreTenants(final VertxTestContext ctx) {

        // GIVEN a service configured with a file name
        props.setFilename(FILE_NAME);
        props.setStartEmpty(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the credentials from the file are loaded
            verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
            ctx.completeNow();
        })));
        svc.start(startFuture);
    }

    /**
     * Verifies that the tenants file written by the registry when persisting the contents can
     * be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testLoadTenantsCanReadOutputOfSaveToFile(final VertxTestContext ctx) {

        // GIVEN a service configured to persist tenants to file
        // that contains some tenants
        props.setFilename(FILE_NAME);
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);

        final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);

        addTenant(Constants.DEFAULT_TENANT)
        .compose(ok -> addTenant("OTHER_TENANT"))
        .compose(ok -> {
            // WHEN saving the content to the file
            doAnswer(invocation -> {
                final Handler<AsyncResult<Void>> handler = invocation.getArgument(2);
                handler.handle(Future.succeededFuture());
                return null;
            }).when(fileSystem).writeFile(eq(FILE_NAME), any(Buffer.class), any(Handler.class));

            return svc.saveToFile();
        })
        .compose(ok -> {
            verify(fileSystem).writeFile(eq(FILE_NAME), buffer.capture(), any(Handler.class));
            // and clearing the tenant registry
            svc.clear();
            return assertTenantDoesNotExist(svc, Constants.DEFAULT_TENANT);
        })
        .compose(ok -> {
            // THEN the tenants can be loaded back in from the file
            doAnswer(invocation -> {
                final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
                handler.handle(Future.succeededFuture(buffer.getValue()));
                return null;
            }).when(fileSystem).readFile(eq(FILE_NAME), any(Handler.class));
            return svc.loadTenantData();
        })
        // and the loaded tenants can be retrieved from the service 
        .compose(ok -> assertTenantExists(svc, Constants.DEFAULT_TENANT))
        .compose(ok -> assertTenantExists(svc, "OTHER_TENANT"))
        .setHandler(ctx.completing());
    }

    /**
     * Verifies that a tenant can be added with <em>modificationEnabled</em>
     * set to {@code false}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsIfModificationIsDisabled(final VertxTestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to add a new tenant
        svc.add(Optional.of("fancy-new-tenant"), new JsonObject(), NoopSpan.INSTANCE, ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the request succeeds
            assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing a tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveTenantsFailsIfModificationIsDisabled(final VertxTestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to update the tenant
        svc.remove("tenant", Optional.empty(), NoopSpan.INSTANCE, ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the update fails
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateTenantsFailsIfModificationIsDisabled(final VertxTestContext ctx) {

        // GIVEN a service containing a set of tenants
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);

        // WHEN trying to update the tenant
        svc.update("tenant", new JsonObject(), null, NoopSpan.INSTANCE, ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the update fails
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Test that a conversion of {@code null} doesn't throw an exception.
     */
    @Test
    public void testNullConversion() {
        assertNull(FileBasedTenantService.convertTenantObject(null));
    }

    /**
     * Test converting tenant objects.
     */
    @Test
    public void testConversion() {
        final TenantObject tenantObject = new TenantObject();
        tenantObject.setEnabled(true);
        final JsonObject tracingConfigJsonObject = new JsonObject();
        tracingConfigJsonObject.put(TenantConstants.FIELD_TRACING_SAMPLING_MODE, "all");
        final JsonObject tracingSamplingModeJsonObject = new JsonObject()
                .put("authId1", "all")
                .put("authId2", "default");
        tracingConfigJsonObject.put(TenantConstants.FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID, tracingSamplingModeJsonObject);
        tenantObject.setProperty(TenantConstants.FIELD_TRACING, tracingConfigJsonObject);

        final Tenant tenant = FileBasedTenantService.convertTenantObject(tenantObject);
        assertEquals(Boolean.TRUE, tenant.getEnabled());
        final TenantTracingConfig tracingConfig = tenant.getTracing();
        assertNotNull(tracingConfig);
        assertEquals(TracingSamplingMode.ALL, tracingConfig.getSamplingMode());
        assertEquals(TracingSamplingMode.ALL, tracingConfig.getSamplingModePerAuthId().get("authId1"));
        assertEquals(TracingSamplingMode.DEFAULT, tracingConfig.getSamplingModePerAuthId().get("authId2"));
    }

}
