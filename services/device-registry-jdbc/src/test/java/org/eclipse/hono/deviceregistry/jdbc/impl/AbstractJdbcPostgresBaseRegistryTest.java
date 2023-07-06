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


package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.nio.file.Path;

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;





@ExtendWith(VertxExtension.class)
abstract class AbstractJdbcPostgresBaseRegistryTest extends AbstractJdbcBaseRegistryTest {

    protected AbstractJdbcPostgresBaseRegistryTest() {
        DEFAULT_DATABASE_TYPE = DatabaseType.POSTGRESQL;
        DATABASE_TYPE = DatabaseType.valueOf(System.getProperty(AbstractJdbcPostgresBaseRegistryTest.class.getSimpleName()
                + ".databaseType", DEFAULT_DATABASE_TYPE.name()).toUpperCase());
        EXAMPLE_SQL_BASE = Path.of("..", "base-jdbc", "src", "main", "resources", "sql", DATABASE_TYPE.name().toLowerCase());
    }

}
