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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * JDBC properties for the device store.
 */
@JsonInclude(value = Include.NON_NULL)
public class JdbcDeviceStoreProperties {

    private JdbcProperties adapter;
    private JdbcProperties management;

    public JdbcProperties getAdapter() {
        return adapter;
    }

    public void setAdapter(final JdbcProperties adapter) {
        this.adapter = adapter;
    }

    public JdbcProperties getManagement() {
        return management;
    }

    public void setManagement(final JdbcProperties management) {
        this.management = management;
    }

}
