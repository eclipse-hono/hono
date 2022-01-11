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


package org.eclipse.hono.service.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;

import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link SniExtensionHelperTest}.
 */
public class SniExtensionHelperTest {

    /**
     * Verifies that all host names are extracted from a TLS session.
     */
    @Test
    public void testGetRequestedHostNamesExtractsAllHostNames() {
        final ExtendedSSLSession session = mock(ExtendedSSLSession.class);
        when(session.getRequestedServerNames()).thenReturn(List.of(
                new SNIHostName("tenant.hono.eclipse.org"),
                new UndefinedServerName(new byte[] { 0x01, 0x02, 0x03 }),
                new SNIHostName("bumlux.eclipse.org")));

        final List<String> hostNames = SniExtensionHelper.getHostNames(session);
        assertThat(hostNames).containsExactly("tenant.hono.eclipse.org", "bumlux.eclipse.org");
    }

    private static class UndefinedServerName extends SNIServerName {

        /**
         * Creates a new instance.
         *
         * @param encodedName The byte representation of the name.
         */
        UndefinedServerName(final byte[] encodedName) {
            super(1, encodedName);
        }
    }
}
