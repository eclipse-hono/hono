/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

/**
 * Verifies behavior of {@link AbstractVertxBasedMqttProtocolAdapter} using the legacy Command & Control endpoint.
 *
 */
@ExtendWith(VertxExtension.class)
public class AbstractVertxBasedMqttProtocolAdapterLegacyEndpointTest extends AbstractVertxBasedMqttProtocolAdapterTest {

    @Override
    protected boolean useLegacyCommandEndpoint() {
        return true;
    }
}
