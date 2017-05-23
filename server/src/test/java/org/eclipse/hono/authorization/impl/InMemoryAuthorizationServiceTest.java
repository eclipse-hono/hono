/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.authorization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.service.authorization.Permission;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Tests verifying behavior of {@link InMemoryAuthorizationService}.
 */
public class InMemoryAuthorizationServiceTest {

    private static final ResourceIdentifier TELEMETRY = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry");
    private static final ResourceIdentifier CONTROL   = ResourceIdentifier.fromStringAssumingDefaultTenant("control");

    private static final String SUBJECT   = "subject";
    private static final String READER    = "reader";
    private static final String WRITER    = "writer";
    private static final String USER_ADMIN = "ADMIN";
    private static final String PERMISSIONS_RESOURCE_PATH = "authorization-service-test-permissions.json";

    private InMemoryAuthorizationService underTest;

    /**
     * Loads permissions from a file.
     * 
     * @throws Exception if the permissions file cannot be read.
     */
    @Before
    public void setUp() throws Exception {

        underTest = new InMemoryAuthorizationService();
        underTest.setPermissionsPath(new ClassPathResource(PERMISSIONS_RESOURCE_PATH));
        underTest.loadPermissions();
    }

    @Test
    public void testHasPermission() throws Exception {

        assertThat(underTest.hasPermission(SUBJECT, TELEMETRY, Permission.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, TELEMETRY, Permission.WRITE)).isFalse();

        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.WRITE)).isFalse();

        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isTrue();
    }

    @Test
    public void testDeviceLevelPermission() throws Exception {

        final ResourceIdentifier TENANT1 = ResourceIdentifier.fromString("telemetry/tenant1");
        final ResourceIdentifier DEVICE1 = ResourceIdentifier.fromString("telemetry/tenant1/device1");

        assertThat(underTest.hasPermission("device1-user", DEVICE1, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission("tenant1-user", DEVICE1, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission("tenant1-user", TENANT1, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission("device1-user", TENANT1, Permission.WRITE)).isFalse();
    }

    @Test
    public void testAddPermission() throws Exception {

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();

        underTest.addPermission(SUBJECT, CONTROL, Permission.READ);

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();
    }

    /**
     * Verifies that a user is authorized to consume telemetry data for a specific tenant if
     * the user has been granted to receive Telemetry data for the wildcard tenant ("*").
     */
    @Test
    public void testHasPermissionReturnsTrueForWildcardTenant() {

        assertThat(underTest.hasPermission(USER_ADMIN, ResourceIdentifier.from("telemetry", "bumlux", "test"), Permission.READ)).isTrue();
    }
}