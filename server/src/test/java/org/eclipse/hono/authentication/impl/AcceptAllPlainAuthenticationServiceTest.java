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

package org.eclipse.hono.authentication.impl;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;

/**
 * Test cases verifying behavior of {@code AcceptAllPlainAuthenticationService}.
 *
 */
public class AcceptAllPlainAuthenticationServiceTest {

    AcceptAllPlainAuthenticationService authService;

    @Before
    public void setUp() {
        authService = new AcceptAllPlainAuthenticationService();
    }

    /**
     * Verifies that the authentication service fails for mechanisms other than PLAIN.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testValidateResponseDetectsUnsupportedMechanism() {
        authService.validateResponse("OTHER", new byte[]{}, res -> {});
    }

    @Test
    public void testValidateResponseFailsForMissingAuthenticationId() {

        authService.validateResponse("PLAIN", getResponse(null, null, "pwd"), res -> {
            assertTrue(res.failed());
        });
    }

    @Test
    public void testValidateResponseFailsForMissingPassword() {

        authService.validateResponse("PLAIN", getResponse(null, "user", null), res -> {
            assertTrue(res.failed());
        });
    }

    @Test
    public void testValidateResponseSucceedsForAnyPassword() {

        authService.validateResponse("PLAIN", getResponse(null, "userA", "pwd"), res -> {
            assertTrue(res.succeeded());
            assertThat(res.result(), is("userA"));
        });
    }

    @Test
    public void testValidateResponseGrantsAuthorizationId() {

        authService.validateResponse("PLAIN", getResponse("userB", "userA", "pwd"), res -> {
            assertTrue(res.succeeded());
            assertThat(res.result(), is("userB"));
        });
    }

    private static byte[] getResponse(final String authzid, final String authcid, final String pwd) {
        ByteBuffer b = ByteBuffer.allocate(100);
        if (authzid != null) {
            b.put(authzid.getBytes(StandardCharsets.UTF_8));
        }
        b.put((byte) 0x00);
        if (authcid != null) {
            b.put(authcid.getBytes(StandardCharsets.UTF_8));
        }
        b.put((byte) 0x00);
        if (pwd != null) {
            b.put(pwd.getBytes(StandardCharsets.UTF_8));
        }
        b.flip();
        byte[] response = new byte[b.remaining()];
        b.get(response);
        return response;
    }
}
