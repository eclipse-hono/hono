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

import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceStoreProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.store.SQL;

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
    public static StoreFactory<TableAdapterStore> adapterStoreFactory() {
        return AdapterStoreFactory.INSTANCE;
    }

    /**
     * Store factory instance for management stores.
     *
     * @return A new instance for an management store.
     */
    public static StoreFactory<TableManagementStore> managementStoreFactory() {
        return ManagementStoreFactory.INSTANCE;
    }

    /**
     * A store factory for creating adapter or management stores.
     *
     * @param <T> The type of store to create.
     */
    public interface StoreFactory<T extends AbstractDeviceStore> {
        /**
         * Create a new flat table store.
         *
         * @param vertx The vertx instance to use.
         * @param tracer The tracer to use.
         * @param properties The configuration properties.
         * @param credentials An optional table name for the credentials table.
         * @param registrations An optional table name for the registrations table.
         * @param groups An optional table name for the groups table.
         * @return The new store.
         *
         * @throws IOException if any IO error occurs when reading the SQL statement configuration.
         */
        T createTable(
                Vertx vertx,
                Tracer tracer,
                JdbcProperties properties,
                Optional<String> credentials,
                Optional<String> registrations,
                Optional<String> groups)
                throws IOException;
    }

    private static final class AdapterStoreFactory implements StoreFactory<TableAdapterStore> {

        private static final StoreFactory<TableAdapterStore> INSTANCE = new AdapterStoreFactory();

        private AdapterStoreFactory() {
        }

        @Override
        public TableAdapterStore createTable(
                final Vertx vertx,
                final Tracer tracer,
                final JdbcProperties properties,
                final Optional<String> credentials,
                final Optional<String> registrations,
                final Optional<String> groups) throws IOException {

            return new TableAdapterStore(
                    JdbcProperties.dataSource(vertx, properties),
                    tracer,
                    Configurations.tableConfiguration(properties.getUrl(), credentials, registrations, groups),
                    SQL.getDatabaseDialect(properties.getUrl()));

        }
    }

    private static final class ManagementStoreFactory implements StoreFactory<TableManagementStore> {

        private static final StoreFactory<TableManagementStore> INSTANCE = new ManagementStoreFactory();

        private ManagementStoreFactory() {
        }

        @Override
        public TableManagementStore createTable(
                final Vertx vertx,
                final Tracer tracer,
                final JdbcProperties properties,
                final Optional<String> credentials,
                final Optional<String> registrations,
                final Optional<String> groups) throws IOException {

            return new TableManagementStore(
                    JdbcProperties.dataSource(vertx, properties),
                    tracer,
                    Configurations.tableConfiguration(properties.getUrl(), credentials, registrations, groups));

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
    public static <T extends AbstractDeviceStore> T store(final Vertx vertx, final Tracer tracer, final JdbcDeviceStoreProperties deviceProperties,
                                                          final Function<JdbcDeviceStoreProperties, JdbcProperties> extractor, final StoreFactory<T> factory) throws IOException {

        final var properties = extractor.apply(deviceProperties);

        final var prefix = Optional.ofNullable(properties.getTableName());
        final var credentials = prefix.map(s -> s + "_credentials");
        final var registrations = prefix.map(s -> s + "_registrations");
        final var groups = prefix.map(s -> s + "_groups");

        return factory.createTable(vertx, tracer, properties, credentials, registrations, groups);

    }

}
