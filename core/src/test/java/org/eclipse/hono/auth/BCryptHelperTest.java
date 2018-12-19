/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.auth;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;


/**
 * Verifies behavior of {@link BCryptHelper}.
 *
 */
public class BCryptHelperTest {

    /**
     * Verifies that the helper detects invalid BCrypt.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetIterationsFailsForInvalidHash() {
        BCryptHelper.getIterations("invalid-hash");
    }

    /**
     * Verifies that the helper detects unsupported BCrypt version.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetIterationsFailsForUnsupportedVersion() {
        BCryptHelper.getIterations("$2y$10$LgDCAvCL1IVbWrIty6RV4.NunlK67mAsj/0d6QXwW4VGD.9qnzU6q");
    }

    /**
     * Verifies that the helper successfully extracts the number of
     * iterations from a valid BCrypt hash.
     */
    @Test
    public void testGetIterationsSucceedsForValidHash() {
        assertThat(
                BCryptHelper.getIterations("$2a$10$LgDCAvCL1IVbWrIty6RV4.NunlK67mAsj/0d6QXwW4VGD.9qnzU6q"),
                is(10));
    }
}
