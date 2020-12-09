/*****************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.AbstractDeviceManagementSearchDevicesTest;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link FileBasedRegistrationService#searchDevices(String, int, int, List, List, Span)}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public final class FileBasedDeviceManagementSearchDevicesTest implements AbstractDeviceManagementSearchDevicesTest {

    private static final String FILE_NAME = "/device-identities.json";

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedDeviceManagementSearchDevicesTest.class);
    private Vertx vertx;
    private FileBasedRegistrationService registrationService;
    private FileBasedRegistrationConfigProperties registrationConfig;
    private FileSystem fileSystem;

    /**
     * Sets up static fixture.
     */
    @BeforeAll
    public void setup() {

        fileSystem = mock(FileSystem.class);
        vertx = mock(Vertx.class);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        registrationConfig = new FileBasedRegistrationConfigProperties();
        registrationConfig.setFilename(FILE_NAME);

        registrationService = new FileBasedRegistrationService(vertx);
        registrationService.setConfig(registrationConfig);
    }

    /**
     * Prints the test name.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
    }


    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.registrationService;
    }

    /**
     * Verifies that a request to search devices with valid page offset succeeds and the result is in accordance with
     * the specified page offset.
     *
     * @param ctx The vert.x test context.
     */
    @Override
    @Test
    // Overridden test because the order of entries in the in-memory store is not consistent.
    public void testSearchDevicesWithPageOffset(final VertxTestContext ctx) {
        final String tenantId = DeviceRegistryUtils.getUniqueIdentifier();
        final int pageSize = 1;
        final int pageOffset = 1;
        final Filter filter = new Filter("/enabled", true);
        final Sort sortOption = new Sort("/id");

        sortOption.setDirection(Sort.Direction.desc);
        createDevices(tenantId, Map.of(
                "testDevice1", new Device().setEnabled(true),
                "testDevice2", new Device().setEnabled(true)))
            .compose(ok -> getDeviceManagementService()
                    .searchDevices(tenantId, pageSize, pageOffset, List.of(filter),
                            List.of(sortOption),
                            NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                    final SearchResult<DeviceWithId> searchResult = s.getPayload();
                    assertThat(searchResult.getTotal()).isEqualTo(2);
                    assertThat(searchResult.getResult()).hasSize(1);
                });
                ctx.completeNow();
            }));
    }
}
