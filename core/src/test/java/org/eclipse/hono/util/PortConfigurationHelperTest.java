/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of the {@link PortConfigurationHelper}.
 *
 */
public class PortConfigurationHelperTest {

    /**
     * Verifies that the helper rejects ports &lt; 0 or &gt; 65535.
     */
    @Test
    public void testIsValidPort() {

        assertTrue(PortConfigurationHelper.isValidPort(0));
        assertTrue(PortConfigurationHelper.isValidPort(65535));

        assertFalse(PortConfigurationHelper.isValidPort(-1));
        assertFalse(PortConfigurationHelper.isValidPort(65536));
    }

}
