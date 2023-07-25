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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.nio.file.Path;


class JdbcPostgresDeviceManagementSearchDevicesTest extends JdbcBasedDeviceManagementSearchDevicesTest {

    protected JdbcPostgresDeviceManagementSearchDevicesTest() {
        super();
        DEFAULT_DATABASE_TYPE = DatabaseType.POSTGRESQL;
        DATABASE_TYPE = DatabaseType.valueOf(System.getProperty(AbstractJdbcRegistryTest.class.getSimpleName()
                + "_postgresql" + ".databaseType", DEFAULT_DATABASE_TYPE.name()).toUpperCase());
        EXAMPLE_SQL_BASE = Path.of("..", "base-jdbc", "src", "main", "resources", "sql", DATABASE_TYPE.name().toLowerCase());
    }

}
