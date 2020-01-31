/**
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
 */


package org.eclipse.hono.tests.http;

import org.eclipse.hono.tests.CommandEndpointConfiguration;


/**
 * A HttpCommandEndpointConfiguration.
 *
 */
public final class HttpCommandEndpointConfiguration extends CommandEndpointConfiguration {

    /**
     * Creates a new configuration.
     * 
     * @param subscriberRole The way in which to subscribe for commands.
     */
    public HttpCommandEndpointConfiguration(final SubscriberRole subscriberRole) {
        super(subscriberRole);
    }

    String getCommandResponseUri(final String reqId) {
        return String.format("/%s/res/%s", getSouthboundEndpoint(), reqId);
    }
}
