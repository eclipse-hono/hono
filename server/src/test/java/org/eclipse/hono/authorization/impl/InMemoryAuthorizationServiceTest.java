package org.eclipse.hono.authorization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.util.Lists;
import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.authorization.Permission;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests InMemoryAuthorizationServiceTest.
 */
public class InMemoryAuthorizationServiceTest {

    public static final String TELEMETRY = "/telemetry";
    public static final String CONTROL   = "/control";

    public static final String SUBJECT   = "subject";
    public static final String READER    = "reader";
    public static final String WRITER    = "writer";
    private AuthorizationService underTest;

    @Before
    public void setUp() throws Exception {
        underTest = new InMemoryAuthorizationService();

        underTest.addPermission(READER, TELEMETRY, Permission.READ);
        underTest.addPermission(WRITER, TELEMETRY, Permission.READ, Permission.WRITE);
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
    public void testHasPermissionInSet() throws Exception {

        final List<String> controlSet = Lists.newArrayList(CONTROL);
        final List<String> telemetrySet = Lists.newArrayList(TELEMETRY);
        final List<String> telemetryAndControlSet = Lists.newArrayList(TELEMETRY, CONTROL);

        assertThat(underTest.hasPermission(READER, telemetrySet, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(READER, telemetryAndControlSet, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(READER, controlSet, Permission.READ)).isFalse();

    }

    @Test
    public void testAddPermission() throws Exception {

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isFalse();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();

        underTest.addPermission(SUBJECT, CONTROL, Permission.READ);

        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(SUBJECT, CONTROL, Permission.WRITE)).isFalse();
    }

    @Test
    public void testRemovePermission() throws Exception {
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isTrue();

        underTest.removePermission(WRITER, TELEMETRY, Permission.WRITE);

        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.READ)).isTrue();
        assertThat(underTest.hasPermission(WRITER, TELEMETRY, Permission.WRITE)).isFalse();
    }
}