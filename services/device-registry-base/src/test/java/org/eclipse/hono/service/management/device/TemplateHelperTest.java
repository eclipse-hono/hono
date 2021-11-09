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
package org.eclipse.hono.service.management.device;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link TemplateHelper}.
 */
public class TemplateHelperTest {

    @Test
    void testForCommonNameSucceeds() {
        final String result = TemplateHelper.fill(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN),
                "CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-121");
    }

    @Test
    void testForCommonNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> TemplateHelper.fill(
                        String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN),
                        "OU=Hono,O=Eclipse"));
    }

    @Test
    void testForOrganizationalUnitNameSucceeds() {
        final String result = TemplateHelper.fill(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU),
                "CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-Hono");
    }

    @Test
    void testForOrganizationalUnitNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> TemplateHelper.fill(
                        String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU),
                        "CN=121,O=Eclipse"));
    }

    @Test
    void testForSubjectDNSucceeds() {
        final String result = TemplateHelper.fill(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_DN),
                "CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-CN=121,OU=Hono,O=Eclipse");
    }

    @Test
    void testWithMultiplePlaceholdersInTemplate() {
        final String result = TemplateHelper.fill(
                String.format("device-%s-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN,
                        RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU),
                "CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-121-Hono");
    }
}
