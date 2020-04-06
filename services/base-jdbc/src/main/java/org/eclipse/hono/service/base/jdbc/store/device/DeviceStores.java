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

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;

/**
 * Helper class for device registry stores.
 */
public final class DeviceStores {

    private DeviceStores() {
    }

    /**
     * Store factory instance for adapter stores.
     *
     * @return A new instance for an adapter store.
     */
    public static StoreFactory<AbstractDeviceAdapterStore> adapterStoreFactory() {
        return AdapterStoreFactory.INSTANCE;
    }

    /**
     * Store factory instance for management stores.
     *
     * @return A new instance for an management store.
     */
    public static StoreFactory<AbstractDeviceManagementStore> managementStoreFactory() {
        return ManagementStoreFactory.INSTANCE;
    }

    /**
     * A store factory for creating adapter or management stores.
     *
     * @param <T> The type of store to create.
     */
    public interface StoreFactory<T extends AbstractDeviceStore> {
        /**
         * Create a new JSON store.
         *
         * @param vertx The vertx instance to use.
         * @param tracer The tracer to use.
         * @param properties The configuration properties.
         * @param hierarchical If the JSON store uses a hierarchical model for a flat one.
         * @return The new store.
         *
         * @throws IOException if any IO error occurs when reading the SQL statement configuration.
         */
        T createJson(Vertx vertx, Tracer tracer, JdbcProperties properties, boolean hierarchical) throws IOException;

        /**
         * Create a new flat table store.
         *
         * @param vertx The vertx instance to use.
         * @param tracer The tracer to use.
         * @param properties The configuration properties.
         * @param credentials An optional table name for the credentials table.
         * @param registrations An optional table name for the registrations table.
         * @return The new store.
         *
         * @throws IOException if any IO error occurs when reading the SQL statement configuration.
         */
        T createTable(Vertx vertx, Tracer tracer, JdbcProperties properties,  Optional<String> credentials, Optional<String> registrations)
                throws IOException;
    }

    private static final class AdapterStoreFactory implements StoreFactory<AbstractDeviceAdapterStore> {

        private static final StoreFactory<AbstractDeviceAdapterStore> INSTANCE = new AdapterStoreFactory();

        private AdapterStoreFactory() {
        }

        @Override
        public AbstractDeviceAdapterStore createJson(final Vertx vertx, final Tracer tracer, final JdbcProperties properties, final boolean hierarchical) throws IOException {
            return new JsonAdapterStore(
                    JdbcProperties.dataSource(vertx, properties),
                    tracer,
                    hierarchical,
                    Configurations.jsonConfiguration(properties.getUrl(), Optional.ofNullable(properties.getTableName()), hierarchical));
        }

        @Override
        public AbstractDeviceAdapterStore createTable(final Vertx vertx, final Tracer tracer, final JdbcProperties properties, final Optional<String> credentials,
                final Optional<String> registrations) throws IOException {
            return new TableAdapterStore(
                    JdbcProperties.dataSource(vertx, properties),
                    tracer,
                    Configurations.tableConfiguration(properties.getUrl(), credentials, registrations));
        }
    }

    private static final class ManagementStoreFactory implements StoreFactory<AbstractDeviceManagementStore> {

        private static final StoreFactory<AbstractDeviceManagementStore> INSTANCE = new ManagementStoreFactory();

        private ManagementStoreFactory() {
        }

        @Override
        public AbstractDeviceManagementStore createJson(final Vertx vertx, final Tracer tracer, final JdbcProperties properties, final boolean hierarchical) throws IOException {
            return new JsonManagementStore(
                    JdbcProperties.dataSource(vertx, properties),
                    tracer,
                    hierarchical,
                    Configurations.jsonConfiguration(properties.getUrl(), Optional.ofNullable(properties.getTableName()), hierarchical));
        }

        @Override
        public AbstractDeviceManagementStore createTable(final Vertx vertx, final Tracer tracer, final JdbcProperties properties, final Optional<String> credentials,
                final Optional<String> registrations) throws IOException {
            return new TableManagementStore(
                    JdbcProperties.dataSource(vertx, properties),
                    tracer,
                    Configurations.tableConfiguration(properties.getUrl(), credentials, registrations));
        }
    }

    /**
     * Create a new data store for the device registry.
     *
     * @param <T> The type of the store.
     * @param vertx The vertx instance to use.
     * @param tracer The tracer to use.
     * @param deviceProperties The configuration properties.
     * @param extractor The extractor, for getting the {@link JdbcProperties} from the overall device
     *        properties.
     * @param factory The store factory.
     *
     * @return A new store factory.
     * @throws IOException if any IO error occurs when reading the SQL statement configuration.
     */
    public static <T extends AbstractDeviceStore> T store(final Vertx vertx, final Tracer tracer, final JdbcDeviceProperties deviceProperties,
            final Function<JdbcDeviceProperties, JdbcProperties> extractor, final StoreFactory<T> factory) throws IOException {

        final var properties = extractor.apply(deviceProperties);

        switch (deviceProperties.getMode()) {
            case JSON_FLAT:
                return factory.createJson(vertx, tracer, properties, false);
            case JSON_TREE:
                return factory.createJson(vertx, tracer, properties, true);
            case TABLE:
                final var prefix = Optional.ofNullable(properties.getTableName());
                final var credentials = prefix.map(s -> s + "_credentials");
                final var registrations = prefix.map(s -> s + "_registrations");
                return factory.createTable(vertx, tracer, properties, credentials, registrations);
            default:
                throw new IllegalStateException(String.format("Unknown store type: %s", deviceProperties.getMode()));
        }

    }

}
