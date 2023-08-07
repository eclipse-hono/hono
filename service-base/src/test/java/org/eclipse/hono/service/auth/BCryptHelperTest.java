/**
 * Copyright (c) 2018, 2023 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;


/**
 * Verifies behavior of {@link BCryptHelper}.
 *
 */
public class BCryptHelperTest {

    /**
     * Verifies that the helper detects invalid BCrypt.
     */
    @Test
    public void testGetCostFactorFailsForInvalidHash() {
        assertThrows(IllegalArgumentException.class, () -> BCryptHelper.getCostFactor("invalid-hash"));
    }

    /**
     * Verifies that the helper detects unsupported BCrypt version.
     */
    @Test
    public void testGetCostFactorFailsForUnsupportedVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> BCryptHelper.getCostFactor("$2y$10$LgDCAvCL1IVbWrIty6RV4.NunlK67mAsj/0d6QXwW4VGD.9qnzU6q"));
    }

    /**
     * Verifies that the helper successfully extracts the cost factor from a valid BCrypt hash.
     */
    @Test
    public void testGetCostFactorSucceedsForValidHash() {
        assertThat(BCryptHelper.getCostFactor("$2a$10$LgDCAvCL1IVbWrIty6RV4.NunlK67mAsj/0d6QXwW4VGD.9qnzU6q"))
            .isEqualTo(10);
    }
}
