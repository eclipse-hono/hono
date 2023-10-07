/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.base.jdbc.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.composer.ComposerException;

/**
 * Testing {@link Statement}.
 */
public class StatementTest {

    /**
     * Test simple field validation.
     */
    @Test
    public void testValidateFields() {
        final Statement statement = Statement.statement("SELECT foo FROM bar WHERE baz=:baz");
        statement.validateParameters("baz");
    }

    /**
     * Test that validating a missing field works.
     */
    @Test
    public void testValidateMissingField() {
        final Statement statement = Statement.statement("SELECT foo FROM bar WHERE baz=:baz");
        assertThrows(IllegalStateException.class, () -> {
            statement.validateParameters("bar");
        });
    }

    /**
     * Test that validating additional fields does not cause an error.
     */
    @Test
    public void testValidateAdditionalField() {
        final Statement statement = Statement.statement("SELECT foo FROM bar WHERE baz=:baz");
        statement.validateParameters("baz", "bar");
    }

    /**
     * Test that a type cast does not get interpreted as named parameter, and fails the validation.
     */
    @Test
    public void testValidateAdditionalField2() {
        final Statement statement = Statement.statement("UPDATE devices\n" +
                "SET\n" +
                "   data=:data::jsonb,\n" +
                "   version=:next_version\n" +
                "WHERE\n" +
                "   tenant_id=:tenant_id\n" +
                "AND\n" +
                "   device_id=:device_id");
        statement.validateParameters("data", "device_id", "next_version", "tenant_id");
    }

    /**
     * Test that we didn't break basic YAML with out override method.
     */
    @Test
    public void testPlainYamlStillWorksForStatementConfig() {

        final String yaml = "read: SELECT 1";

        final var cfg = StatementConfiguration.empty()
                .overrideWith(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), false);

        assertEquals("SELECT 1", cfg.getRequiredStatement("read").expand().getSql());

    }

    /**
     * The creating a file via the value of a map item.
     *
     * @param tempDir A temporary directory.
     */
    @Test
    public void testObjectCreationRejectedMapValue(@TempDir final Path tempDir) {
        final Path markerFile = tempDir.resolve("testObjectCreationRejectedMapValue.marker");
        final String yaml = "read: !!java.io.FileOutputStream [" + markerFile.toAbsolutePath().toString() + "]";

        assertNoMarkerFile(markerFile, yaml);
    }

    /**
     * The creating a file via a plain value.
     *
     * @param tempDir A temporary directory.
     */
    @Test
    public void testObjectCreationRejectedPlainValue(@TempDir final Path tempDir) {
        final Path markerFile = tempDir.resolve("testObjectCreationRejectedPlainValue.marker");
        final String yaml = "!!java.io.FileOutputStream [" + markerFile.toAbsolutePath().toString() + "]";

        assertNoMarkerFile(markerFile, yaml);
    }

    private void assertNoMarkerFile(final Path markerFile, final String yaml) {
        Exception expected = null;
        try {
            final var cfg = StatementConfiguration.empty();
            cfg.overrideWith(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), false);
        } catch (Exception e) {
            // delay test for later
            expected = e;
        }

        assertFalse(Files.isRegularFile(markerFile), "Marker file must not exist");
        assertNotNull(expected);
        assertThat(expected).isInstanceOf(ComposerException.class);
    }

}
