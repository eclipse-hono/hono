/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.http;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpServerRequest;

/**
 * Test verifying functionality of the {@link HttpUtils} class.
 */
public class HttpUtilsTest {

    /**
     * Verifies that trying to get the absolute URI for a request with
     * an invalid URI doesn't return {@code null}.
     */
    @Test
    public void testGetAbsoluteURIReturnsNotNullForInvalidURI() {
        final String scheme = "http";
        final String host = "localhost";
        final String relativeUri = "/index.php?s=/Index/\\think";
        assertThrows(URISyntaxException.class, () -> new URI(relativeUri));

        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.uri()).thenReturn(relativeUri);
        when(req.scheme()).thenReturn(scheme);
        when(req.host()).thenReturn(host);
        final String absoluteURI = HttpUtils.getAbsoluteURI(req);

        assertThat(absoluteURI).isEqualTo(scheme + "://" + host + relativeUri);
    }

}
