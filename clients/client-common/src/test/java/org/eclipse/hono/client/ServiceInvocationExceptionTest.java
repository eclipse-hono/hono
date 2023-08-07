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

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;

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

    /**
     * Verifies that the extracted status code matches that of the given instance of
     * {@link ServiceInvocationException}.
     */
    @Test
    public void testExtractStatusCodeFromServiceInvocationException() {

        final ServiceInvocationException exception = new ServiceInvocationException(
                HttpURLConnection.HTTP_NOT_FOUND) {
                    private static final long serialVersionUID = 1L;
        };
        assertThat(ServiceInvocationException.extractStatusCode(exception)).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
    }

    /**
     * Verifies that the extracted status code is {@link HttpURLConnection#HTTP_INTERNAL_ERROR}
     * when the given exception is not an instance of {@link ServiceInvocationException}.
     */
    @Test
    public void testExtractStatusCodeFromNonServiceInvocationException() {

        assertThat(ServiceInvocationException.extractStatusCode(new Exception()))
                .isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    /**
     * Verifies that the extracted status code matches {@link HttpURLConnection#HTTP_INTERNAL_ERROR}
     * when the given exception is{@code null}.
     */
    @Test
    public void testExtractStatusCodeWhenExceptionIsNull() {

        assertThat(ServiceInvocationException.extractStatusCode(null))
                .isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
    }
}
