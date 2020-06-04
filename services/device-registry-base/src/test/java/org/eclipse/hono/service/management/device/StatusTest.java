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

package org.eclipse.hono.service.management.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;


/**
 * Verifies {@link Status}.
 */
class StatusTest {

    /**
     * Encode with default values.
     */
    @Test
    public void testEncodeDefault() {
        final var json = JsonObject.mapFrom(new Status());
        assertThat(json).isNotNull();
        assertThat(json.getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)).isNotNull();
        assertThat(json.getInstant(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE)).isNotNull();
        assertThat(json.getString(RegistryManagementConstants.FIELD_STATUS_LAST_USER)).isNull();
    }

    /**
     * Encode with all values present.
     */
    @Test
    public void testEncodeEnabled() {
        final var json = JsonObject.mapFrom(new Status().update("DEFAULT_TENANT"));
        assertThat(json).isNotNull();
        assertThat(json.getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)).isNotNull();
        assertThat(json.getInstant(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE)).isNotNull();
        assertThat(json.getString(RegistryManagementConstants.FIELD_STATUS_LAST_USER)).isEqualTo("DEFAULT_TENANT");
    }

}
