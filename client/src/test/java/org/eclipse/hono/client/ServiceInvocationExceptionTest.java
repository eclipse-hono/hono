/*******************************************************************************
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

package org.eclipse.hono.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link ServiceInvocationException}.
 */
public class ServiceInvocationExceptionTest {

    /**
     * Verifies that the localized message for a known ServiceInvocationException
     * resource key can be obtained.
     */
    @Test
    public void testGetLocalizedMessage() {
        final String key = SendMessageTimeoutException.CLIENT_FACING_MESSAGE_KEY;

        // assert that getLocalizedMessage doesn't return the key as fallback
        assertThat(ServiceInvocationException.getLocalizedMessage(key)).isNotEqualTo(key);
    }
}
