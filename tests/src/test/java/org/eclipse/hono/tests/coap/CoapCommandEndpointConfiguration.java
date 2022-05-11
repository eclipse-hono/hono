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

    String getCommandResponseUri(
            final int msgNo,
            final String tenantId,
            final String deviceId,
            final String reqId) {

        final boolean isEven = msgNo % 2 == 0;
        final String endpointName = isEven ?
                CommandConstants.COMMAND_RESPONSE_ENDPOINT : CommandConstants.COMMAND_RESPONSE_ENDPOINT_SHORT;
        if (isSubscribeAsGateway() || isSubscribeAsUnauthenticatedDevice()) {
            String tenantToUse = tenantId;
            if (isSubscribeAsGateway() && isEven) {
                tenantToUse = "";
            }
            return String.format("/%s/%s/%s/%s", endpointName, tenantToUse, deviceId, reqId);
        }
        return String.format("/%s/%s", endpointName, reqId);
    }


}
