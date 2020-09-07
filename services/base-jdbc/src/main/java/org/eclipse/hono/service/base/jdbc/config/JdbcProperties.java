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

package org.eclipse.hono.service.base.jdbc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;

/**
 * Configuration properties for a JDBC service.
 */
@JsonInclude(value = Include.NON_NULL)
public class JdbcProperties {

    private static final Logger log = LoggerFactory.getLogger(JdbcProperties.class);

    private String url;
    private String driverClass;
    private String username;
    private String password;
    private Integer maximumPoolSize;

    private String tableName;

    public void setUrl(final String url) {
        this.url = url;
    }
    public String getUrl() {
        return url;
    }

    public void setDriverClass(final String driverClassName) {
        this.driverClass = driverClassName;
    }
    public String getDriverClass() {
        return driverClass;
    }

    public void setUsername(final String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }

    public void setPassword(final String password) {
        this.password = password;
    }
    public String getPassword() {
        return password;
    }

    public void setMaximumPoolSize(final Integer maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }
    public Integer getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public String getTableName() {
        return tableName;
    }
    public void setTableName(final String tableName) {
        this.tableName = tableName;
    }

    /**
     * Create a {@link SQLClient} from the configuration properties.
     * @param vertx The vertx instance to use.
     * @param dataSourceProperties The properties.
     * @return The new SQL client.
     */
    public static JDBCClient dataSource(final Vertx vertx, final JdbcProperties dataSourceProperties) {

        final JsonObject config = new JsonObject()
                .put("url", dataSourceProperties.getUrl())
                .put("user", dataSourceProperties.getUsername());

        // password is added later, after logging

        if (dataSourceProperties.getDriverClass() != null) {
            config.put("driver_class", dataSourceProperties.getDriverClass());
        }
        if (dataSourceProperties.getMaximumPoolSize() != null) {
            config.put("max_pool_size", dataSourceProperties.getMaximumPoolSize());
        }

        log.info("Creating new SQL client: {} - table: {}", config, dataSourceProperties.getTableName());

        // put password after logging

        config
            .put("password", dataSourceProperties.getPassword());

        // create new client

        return JDBCClient.create(vertx, config);

    }

}
