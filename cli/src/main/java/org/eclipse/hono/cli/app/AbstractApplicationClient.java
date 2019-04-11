/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.app;

import java.util.Objects;

import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * A base class for implementing command line clients that access Hono's
 * northbound APIs.
 *
 */
abstract class AbstractApplicationClient extends AbstractCliClient {

    @Value(value = "${tenant.id}")
    protected String tenantId;
    @Value(value = "${device.id}")
    protected String deviceId;
    @Value(value = "${connection.retryInterval}")
    protected int connectionRetryInterval;
    protected ApplicationClientFactory clientFactory;

    @Autowired
    public final void setHonoConnection(final ApplicationClientFactory connection) {
        this.clientFactory = Objects.requireNonNull(connection);
    }
}
