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

package org.eclipse.hono.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.hono.util.PortConfigurationHelper;
import org.junit.Test;

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

        assertTrue("Lower inclusive bound", PortConfigurationHelper.isValidPort(0));
        assertTrue("Upper inclusive bound", PortConfigurationHelper.isValidPort(65535));

        assertFalse("Lower exclusive bound", PortConfigurationHelper.isValidPort(-1));
        assertFalse("Upper exclusive bound", PortConfigurationHelper.isValidPort(65536));
    }

}
