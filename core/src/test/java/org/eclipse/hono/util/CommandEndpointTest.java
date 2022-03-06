/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link CommandEndpoint}.
 *
 */
class CommandEndpointTest {

    @Test
    void testGetFormattedUrlInsertsDeviceId() {

        final CommandEndpoint endpoint = new CommandEndpoint();
        endpoint.setUri("https://hono.eclipseprojects.io/command/{{deviceId}}/{{deviceId}}");
        assertThat(endpoint.getFormattedUri("the-device"))
            .isEqualTo("https://hono.eclipseprojects.io/command/the-device/the-device");
    }

}
