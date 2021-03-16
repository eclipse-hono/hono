/*
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

package org.eclipse.hono.client.kafka;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common configuration properties for Kafka clients.
 */
public class KafkaConfigProperties {

    /**
     * The default prefix to use for creating Kafka client IDs.
     */
    public static final String DEFAULT_CLIENT_ID_PREFIX = "hono";

    protected static final String PROPERTY_BOOTSTRAP_SERVERS = "bootstrap.servers";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Client id prefix to be applied if no {@code client.id} property is set in the common
     * or specific client config properties.
     */
    protected String defaultClientIdPrefix = DEFAULT_CLIENT_ID_PREFIX;

    private final Properties propsFromFile = new Properties();

    /**
     * Post-processes the configuration properties.
     * <p>
     * Invoked by {@link #getConfig()} with the properties that have been loaded from the configured files.
     * <p>
     * This default implementation does nothing.
     *
     * @param configuration The configuration properties.
     */
    protected void postProcessConfiguration(final Map<String, String> configuration) {
    }

    /**
     * Sets the paths to files from which to load Kafka client configuration properties.
     * <p>
     * The files will be read in the given order.
     *
     * @param files The paths to the files.
     * @throws NullPointerException if files is {@code null}.
     * @throws IllegalArgumentException if any of the files cannot be read.
     */
    public final void setPropertyFiles(final List<String> files) {
        Objects.requireNonNull(files);
        synchronized (propsFromFile) {
            files.forEach(path -> loadPropertiesFromFile(path, propsFromFile));
        }
    }

    private void loadPropertiesFromFile(final String path, final Properties props) {
        try (InputStream in = new FileInputStream(path)) {
            props.load(in);
            log.info("successfully loaded Kafka client configuration from file [{}]", path);
        } catch (final FileNotFoundException e) {
            log.info("could not find property file [{}], ignoring ...", path);
            throw new IllegalArgumentException("could not find property file", e);
        } catch (IOException e) {
            log.info("failed to load Kafka client configuration from file [{}], ignoring ...", path, e);
            throw new IllegalArgumentException("failed to load properties from file", e);
        }
    }

    /**
     * Checks if this client configuration specifies Kafka bootstrap servers.
     *
     * @return {@code true} if this configuration contains a property with name {@value #PROPERTY_BOOTSTRAP_SERVERS}.
     */
    public final boolean isConfigured() {
        return propsFromFile.containsKey(PROPERTY_BOOTSTRAP_SERVERS);
    }

    /**
     * Gets the Kafka client configuration properties.
     * <p>
     * The returned map contains the properties loaded from the configured {@linkplain #setPropertyFiles(List)
     * property files}.
     *
     * @return The configuration properties.
     */
    public final Map<String, String> getConfig() {
        final Map<String, String> config = new HashMap<>();
        synchronized (propsFromFile) {
            propsFromFile.forEach((k, v) -> {
                if (k instanceof String && v instanceof String) {
                    config.put((String) k, (String) v);
                }
            });
        }
        postProcessConfiguration(config);
        return config;
    }

    /**
     * Sets the prefix for the client ID that is passed to the Kafka server to allow application specific server-side
     * request logging.
     * <p>
     * If the common or specific client config already contains a value for key {@code client.id}, that one
     * will be used and the parameter here will be ignored.
     * <p>
     * The default value of this property is {@value #DEFAULT_CLIENT_ID_PREFIX}.
     *
     * @param clientId The client ID prefix to set.
     * @throws NullPointerException if the client ID is {@code null}.
     */
    public final void setDefaultClientIdPrefix(final String clientId) {
        this.defaultClientIdPrefix = Objects.requireNonNull(clientId);
    }

    /**
     * Overrides a property in the given map.
     *
     * @param config The map to set the property in.
     * @param key The key of the property.
     * @param value The property value.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void overrideConfigProperty(final Map<String, String> config, final String key, final String value) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        log.trace("setting Kafka config property [{}={}]", key, value);
        final Object oldValue = config.put(key, value);
        if (oldValue != null) {
            log.debug("provided Kafka configuration contains property [{}={}], changing it to [{}]", key,
                    oldValue, value);
        }
    }

    /**
     * Sets a client id property with a unique value in the given map.
     * <p>
     * An already set client id property or alternatively the value set via
     * {@link #setDefaultClientIdPrefix(String)} will be used as a prefix, to which
     * the given clientName and a UUID will be added.
     *
     * @param config The map to set the client id in.
     * @param clientName The client name to include in the client id.
     * @param clientIdPropertyName The name of the client id property.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void setUniqueClientId(final Map<String, String> config, final String clientName,
            final String clientIdPropertyName) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(clientName);
        Objects.requireNonNull(clientIdPropertyName);

        final UUID uuid = UUID.randomUUID();
        final String clientIdPrefix = Optional.ofNullable(config.get(clientIdPropertyName))
                .orElse(defaultClientIdPrefix);
        if (Strings.isNullOrEmpty(clientIdPrefix)) {
            config.put(clientIdPropertyName, String.format("%s-%s", clientName, uuid));
        } else {
            config.put(clientIdPropertyName, String.format("%s-%s-%s", clientIdPrefix, clientName, uuid));
        }
    }
}
