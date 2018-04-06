/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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
