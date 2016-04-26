package org.eclipse.hono.authorization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.authorization.Permission;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests InMemoryAuthorizationServiceTest.
 */
public class InMemoryAuthorizationServiceTest {

    public static final String TELEMETRY = "/telemetry";
    public static final String CONTROL  = "/control";
    public static final String READER    = "reader";
    public static final String WRITER    = "writer";
    public static final String ADMIN     = "admin";
    public static final String SUBJECT     = "subject";
    private AuthorizationService underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new InMemoryAuthorizationService();

        underTest.addPermission(READER, TELEMETRY, Permission.READ);
        underTest.addPermission(WRITER, TELEMETRY, Permission.READ, Permission.WRITE);
        underTest.addPermission(ADMIN, TELEMETRY, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);
    }

    @Test
    public void testHasPermission() throws Exception {

        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.WRITE)).isFalse();
        assertThat(underTest.hasPermission(READER, TELEMETRY, Permission.ADMINISTRATE)).isFalse();

        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.ADMINISTRATE)).isFalse();

        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.ADMINISTRATE)).isTrue();

    }

    @Test
    public void testAddPermission() throws Exception {

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.ADMINISTRATE)).isFalse();

        underTest.addPermission(SUBJECT, CONTROL, Permission.READ);

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.ADMINISTRATE)).isFalse();
    }

    @Test
    public void testRemovePermission() throws Exception {
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.ADMINISTRATE)).isTrue();

        underTest.removePermission(ADMIN, TELEMETRY, Permission.ADMINISTRATE);

        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.WRITE)).isTrue();
        assertThat(underTest.hasPermission(ADMIN, TELEMETRY, Permission.ADMINISTRATE)).isFalse();
    }
}