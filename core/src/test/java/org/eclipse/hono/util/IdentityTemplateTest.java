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
package org.eclipse.hono.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link IdentityTemplate}.
 */
public class IdentityTemplateTest {

    @Test
    void testForCommonNameSucceeds() {
        final IdentityTemplate template = new IdentityTemplate(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN));
        final String result = template.apply("CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-121");
    }

    @Test
    void testForCommonNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    final IdentityTemplate template = new IdentityTemplate(
                            String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN));
                    template.apply("OU=Hono,O=Eclipse");
                });
    }

    @Test
    void testForOrganizationalUnitNameSucceeds() {
        final IdentityTemplate template = new IdentityTemplate(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU));
        final String result = template.apply("CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-Hono");
    }

    @Test
    void testForOrganizationalUnitNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    final IdentityTemplate template = new IdentityTemplate(
                            String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU));
                    template.apply("CN=121,O=Eclipse");
                });
    }

    @Test
    void testForOrganizationNameSucceeds() {
        final IdentityTemplate template = new IdentityTemplate(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_O));
        final String result = template.apply("CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-Eclipse");
    }

    @Test
    void testForOrganizationNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    final IdentityTemplate template = new IdentityTemplate(
                            String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_O));
                    template.apply(
                            "CN=121,OU=Hono");
                });
    }

    @Test
    void testForSubjectDNSucceeds() {
        final IdentityTemplate template = new IdentityTemplate(
                String.format("device-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_DN));
        final String result = template.apply(
                "CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-CN=121,OU=Hono,O=Eclipse");
    }

    @Test
    void testWithMultiplePlaceholdersInTemplate() {
        final IdentityTemplate template = new IdentityTemplate(
                String.format("device-%s-%s", RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN,
                        RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU));
        final String result = template.apply(
                "CN=121,OU=Hono,O=Eclipse");
        assertThat(result).isEqualTo("device-121-Hono");
    }

    @Test
    void testWithUnsupportedPlaceHoldersInTemplate() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityTemplate(
                        String.format("device-{{subject-xyz}}-%s-{{unsupported}}",
                                RegistryManagementConstants.PLACEHOLDER_SUBJECT_OU)));
    }
}
