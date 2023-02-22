/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * A configuration mechanism for making SQL statements configurable.
 */
public class StatementConfiguration {

    /**
     * The default path to read SQL configuration from.
     */
    public static final Path DEFAULT_PATH = Paths.get("/etc/config/sql");

    private static final boolean SKIP_DUMPING_CONFIG = Boolean.getBoolean("org.eclipse.hono.service.base.jdbc.store.skipDumpingStatementConfiguration");
    private static final Logger log = LoggerFactory.getLogger(StatementConfiguration.class);

    private final Map<String, Statement> statements;
    private final Object[] formatArguments;

    private StatementConfiguration(final Map<String, Statement> statements, final Object[] formatArguments) {
        this.statements = statements;
        this.formatArguments = formatArguments;
    }

    /**
     * Override the current state with additional configuration.
     * <p>
     * The state of this instance will not be changed. The overridden configuration will be returned.
     *
     * @param path The file to read the configuration from.
     * @param ignoreMissing Ignore if the resource is missing.
     * @return A new instance, with the overridden configuration.
     * @throws IOException in case an IO error occurs.
     */
    public StatementConfiguration overrideWith(final Path path, final boolean ignoreMissing) throws IOException {

        if (ignoreMissing && !Files.exists(path)) {
            log.info("Ignoring missing statement configuration file: {}", path);
            return this;
        }

        log.info("Loading statement configuration file: {}", path);

        try (InputStream input = Files.newInputStream(path)) {
            return overrideWith(input, false);
        }

    }

    /**
     * Override the current state with additional configuration.
     * <p>
     * The state of this instance will not be changed. The overridden configuration will be returned.
     *
     * @param input The input stream to read the configuration from.
     * @param ignoreMissing Ignore if the resource is missing.
     * @return A new instance, with the overridden configuration.
     * @throws IllegalArgumentException if the input is null, and the {@code ignoreMissing} parameter is
     *         not {@code true}.
     */
    public StatementConfiguration overrideWith(final InputStream input, final boolean ignoreMissing) {
        if (input == null) {
            if (ignoreMissing) {
                return this;
            } else {
                throw new IllegalArgumentException("Missing input");
            }
        }

        final Yaml yaml = createYamlParser();
        /*
         * we must load using "load(input)" not "loadAs(input, Map.class)", because the
         * latter would require to construct a class (Map) by name, which we do not support.
         */
        final Map<String, Object> properties = yaml.load(input);
        if (properties == null) {
            // we could read the source, but it was empty
            return this;
        }
        return overrideWith(properties);
    }

    private StatementConfiguration overrideWith(final Map<String, Object> properties) {

        final Map<String, Statement> result = new HashMap<>(this.statements);

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            final String key = entry.getKey();

            // if key is set, but not a string ...
            if (entry.getValue() != null && !(entry.getValue() instanceof String)) {
                // ... fail
                throw new IllegalArgumentException(String.format("Key '%s' is not of type string: %s", entry.getKey(), entry.getValue().getClass()));
            }

            final String value = entry.getValue() != null ? entry.getValue().toString() : null;
            if (value == null || value.isBlank()) {
                // remove key
                result.remove(key);
                continue;
            }

            final Statement s = Statement.statement(value, this.formatArguments);
            result.put(key, s);
        }

        return new StatementConfiguration(result, this.formatArguments);
    }

    /**
     * Optionally get a statement from the configuration.
     *
     * @param key The key of the statement.
     * @return The optional statement. May be {@link Optional#empty()}, but never {@code null}.
     */
    public Optional<Statement> getStatement(final String key) {
        return Optional.ofNullable(this.statements.get(key));
    }

    /**
     * Get a statement from the configuration.
     *
     * @param key The key of the statement.
     * @return The statement, never returns {@code null}.
     * @throws IllegalArgumentException if the statement is not present in the configuration.
     */
    public Statement getRequiredStatement(final String key) {
        return getStatement(key)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Statement with key '%s' not found", key)));
    }

    /**
     * And empty statement configuration.
     * <p>
     * This is intended to be use as a starting point, chaining several calls to the different
     * "override" methods.
     *
     * @param formatArguments Arguments which will be used to replace positional
     *        {@link String#format(String, Object...)} arguments in the SQL statement configuration.
     *        This may be used to replace things like table names, which cannot be provided as SQL
     *        statement parameters.
     * @return A new, empty, statement configuration instance.
     */
    public static StatementConfiguration empty(final Object... formatArguments) {
        return new StatementConfiguration(Collections.emptyMap(), formatArguments);
    }

    /**
     * Default pattern of overriding configuration.
     * <p>
     * This will override the configuration from the following locations:
     * <ul>
     * <li>{@code classpath://package.to.clazz/<basename>.sql.yaml} (required)</li>
     * <li>{@code classpath://package.to.clazz/<basename>.<dialect>.sql.yaml} (optional)</li>
     * <li>{@code file://<path>/<basename>.<dialect>.sql.yaml} (optional)</li>
     * </ul>
     *
     * @param basename The basename.
     * @param dialect The SQL dialect.
     * @param clazz The root location in the classpath.
     * @param path The local file system path.
     * @return An overridden configuration.
     * @throws IOException In case of any IO error.
     */
    public StatementConfiguration overrideWithDefaultPattern(final String basename, final String dialect, final Class<?> clazz, final Path path) throws IOException {

        final String base = basename + ".sql.yaml";
        final String dialected = basename + "." + dialect + ".sql.yaml";

        try (
                InputStream resource = clazz.getResourceAsStream(base);
                InputStream dialectResource = clazz.getResourceAsStream(dialected);) {

            final Path overridePath = path.resolve(basename + ".sql.yaml");

            log.info("Loading - class: {}, name: {}, input: {}", clazz, base, resource);
            log.info("Loading - class: {}, name: {}, input: {}", clazz, dialected, dialectResource);
            log.info("Loading - path: {}", overridePath);

            return this
                    .overrideWith(resource, false)
                    .overrideWith(dialectResource, true)
                    .overrideWith(overridePath, true);
        }

    }

    /**
     * Dump the configuration to a logger.
     *
     * @param logger The logger to dump to.
     */
    public void dump(final Logger logger) {

        if (SKIP_DUMPING_CONFIG) {
            return;
        }
        if (logger.isDebugEnabled()) {
            final StringBuilder b = new StringBuilder("Dumping statement configuration")
                    .append(System.lineSeparator());
            b.append(String.format("Format arguments: %s", this.formatArguments))
                .append(System.lineSeparator());

            b.append("Statements:").append(System.lineSeparator());
            final String[] keys = this.statements.keySet().toArray(String[]::new);
            Arrays.sort(keys);
            b.append(Arrays.stream(keys)
                    .map(key -> String.format("name: %s%s%s%s",
                            System.lineSeparator(), key, System.lineSeparator(), this.statements.get(key)))
                    .collect(Collectors.joining(System.lineSeparator())));
            logger.debug(b.toString());
        }
    }

    /**
     * Create a YAML parser which reject creating arbitrary Java objects.
     *
     * @return A new YAML parser, never returns {@code null}.
     */
    static Yaml createYamlParser() {
        return new Yaml(new Constructor(new LoaderOptions()) {
            @Override
            protected Class<?> getClassForName(final String name) throws ClassNotFoundException {
                throw new IllegalArgumentException("Class instantiation is not supported");
            }
        });
    }

}
