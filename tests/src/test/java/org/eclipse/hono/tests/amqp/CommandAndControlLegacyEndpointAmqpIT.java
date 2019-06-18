/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.amqp;

import org.junit.runner.RunWith;

import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for sending commands to a device connected to the AMQP adapter using the legacy Command & Control
 * endpoint.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandAndControlLegacyEndpointAmqpIT extends CommandAndControlAmqpIT {

    @Override
    protected boolean useLegacyCommandEndpoint() {
        return true;
    }
}
