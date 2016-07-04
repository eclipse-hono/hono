/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import static org.junit.Assert.*;

import static org.hamcrest.CoreMatchers.is;

import org.junit.Test;

public class ResourceIdentifierTest {

    @Test
    public void testFromStringAllowsMissingDeviceId() {
        ResourceIdentifier resourceId = ResourceIdentifier.fromString("telemetry/myTenant");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertNull(resourceId.getDeviceId());
        assertThat(resourceId.toString(), is("telemetry/myTenant"));
    }

    @Test
    public void testFromStringAssumingDefaultTenantAllowsMissingDeviceId() {
        ResourceIdentifier resourceId = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry/");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is(Constants.DEFAULT_TENANT));
        assertNull(resourceId.getDeviceId());
        assertThat(resourceId.toString(), is("telemetry/" + Constants.DEFAULT_TENANT));
    }

    @Test
    public void testFromIndividualParameters() {
        ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "myTenant", "myDevice");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertThat(resourceId.getDeviceId(), is("myDevice"));
        assertThat(resourceId.toString(), is("telemetry/myTenant/myDevice"));
    }

    @Test
    public void testFromAllowsMissingDeviceId() {
        ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "myTenant", null);
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is("myTenant"));
        assertNull(resourceId.getDeviceId());
        assertThat(resourceId.toString(), is("telemetry/myTenant"));
    }

    @Test
    public void testEqualsReturnsTrueForSameEndpointAndTenant() {
        ResourceIdentifier one = ResourceIdentifier.from("ep", "tenant", null);
        ResourceIdentifier two = ResourceIdentifier.from("ep", "tenant", null);
        assertTrue(one.equals(two));

        one = ResourceIdentifier.fromString("ep/tenant");
        two = ResourceIdentifier.fromString("ep/tenant");
        assertTrue(one.equals(two));
    }

    @Test
    public void testHashCodeReturnsSameValueForSameEndpointAndTenant() {
        ResourceIdentifier one = ResourceIdentifier.from("ep", "tenant", null);
        ResourceIdentifier two = ResourceIdentifier.from("ep", "tenant", null);
        assertThat(one.hashCode(), is(two.hashCode()));

        one = ResourceIdentifier.fromString("ep/tenant");
        two = ResourceIdentifier.fromString("ep/tenant");
        assertThat(one.hashCode(), is(two.hashCode()));
    }

    @Test
    public void testToPathStripsTrailingNulls() {
        ResourceIdentifier id = ResourceIdentifier.fromPath(new String[]{"first", "second", null, null});
        assertThat(id.toPath().length, is(2));
        assertThat(id.toPath()[0], is("first"));
        assertThat(id.toPath()[1], is("second"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToPathFailsForNonTrailingNulls() {
        ResourceIdentifier.fromPath(new String[]{"first", "second", null, "last"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToPathFailsForPathStartingWithNullSegment() {
        ResourceIdentifier.fromPath(new String[]{null, "second", "last"});
    }
}
