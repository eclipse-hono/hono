/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.util;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.client.ServiceInvocationException;

/**
 * A collection of assertions useful for testing device registry implementations.
 *
 */
public final class Assertions {

    private Assertions() {
        // prevent instantiation
    }

    /**
     * Asserts that an error is a {@link ServiceInvocationException} with a status code.
     *
     * @param error The error to assert.
     * @param expectedStatusCode The expected status code.
     * @throws AssertionError if any of the assertions fail.
     */
    public static void assertServiceInvocationException(
            final Throwable error,
            final int expectedStatusCode) {
        assertThat(error).isInstanceOf(ServiceInvocationException.class);
        assertThat(((ServiceInvocationException) error).getErrorCode()).isEqualTo(expectedStatusCode);
    }

}
