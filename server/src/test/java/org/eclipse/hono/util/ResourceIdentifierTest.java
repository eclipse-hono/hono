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
    }

    @Test
    public void testFromStringAssumingDefaultTenantAllowsMissingDeviceId() {
        ResourceIdentifier resourceId = ResourceIdentifier.fromStringAssumingDefaultTenant("telemetry/");
        assertNotNull(resourceId);
        assertThat(resourceId.getEndpoint(), is("telemetry"));
        assertThat(resourceId.getTenantId(), is(Constants.DEFAULT_TENANT));
        assertNull(resourceId.getDeviceId());
    }
}
