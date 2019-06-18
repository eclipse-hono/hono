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
package org.eclipse.hono.adapter.amqp.impl;

import org.junit.runner.RunWith;

import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies the behavior of {@link VertxBasedAmqpProtocolAdapter} using the legacy Command & Control endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedAmqpProtocolAdapterLegacyEndpointTest extends VertxBasedAmqpProtocolAdapterTest {

    @Override
    protected boolean useLegacyCommandEndpoint() {
        return true;
    }
}
