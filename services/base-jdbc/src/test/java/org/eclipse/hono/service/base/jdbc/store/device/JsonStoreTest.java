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

package org.eclipse.hono.service.base.jdbc.store.device;

import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

/**
 * Test {@link JsonAdapterStore} methods.
 */
public class JsonStoreTest {

    private static PskCredential newPskCredential(final String authId) {

        final PskSecret psk1 = new PskSecret();
        psk1.setKey("foo".getBytes(StandardCharsets.UTF_8));

        final PskCredential result = new PskCredential(authId, List.of(psk1));

        return result;
    }

    /**
     * Test converting hierarchical data structure.
     */
    @Test
    public void testHierarchical() {

        final PskCredential psk = newPskCredential("auth-1");
        final String encoded = JsonAdapterStore.encodeCredentialsHierarchical(Arrays.asList(psk));

        assertThat(encoded, Is.is("{\"psk\":{\"auth-1\":{\"secrets\":[{\"key\":\"Zm9v\"}]}}}"));

        final List<CommonCredential> decoded = JsonAdapterStore.decodeCredentialsHierarchical(encoded);

        assertThat(decoded, IsCollectionWithSize.hasSize(1));
    }

}
