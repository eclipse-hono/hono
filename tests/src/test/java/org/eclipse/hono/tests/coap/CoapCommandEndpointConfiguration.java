/**
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
 */


package org.eclipse.hono.tests.coap;

import org.eclipse.hono.tests.CommandEndpointConfiguration;
import org.eclipse.hono.util.CommandConstants;


/**
 * CoAP adapter specific configuration properties for defining variants of Command &amp; Control
 * related test scenarios.
 *
 */
public final class CoapCommandEndpointConfiguration extends CommandEndpointConfiguration {

    /**
     * Creates a new configuration.
     *
     * @param subscriberRole The way in which to subscribe for commands.
     */
    public CoapCommandEndpointConfiguration(final SubscriberRole subscriberRole) {
        super(subscriberRole);
    }

    String getCommandResponseUri(final String tenantId, final String deviceId, final String reqId) {
        if (isSubscribeAsGateway() || isSubscribeAsUnauthenticatedDevice()) {
            return String.format("/%s/%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId, deviceId, reqId);
        }
        return String.format("/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, reqId);
    }
}
