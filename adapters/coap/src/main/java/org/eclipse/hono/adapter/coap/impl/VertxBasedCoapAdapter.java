/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap.impl;

import org.eclipse.hono.adapter.coap.AbstractVertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.util.Constants;

/**
 * A vert.x based Hono protocol adapter providing access to Hono's south bound
 * Telemetry &amp; Event API by means of CoAP resources.
 */
public final class VertxBasedCoapAdapter extends AbstractVertxBasedCoapAdapter<CoapAdapterProperties> {

    /**
     * {@inheritDoc}
     *
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_COAP}
     */
    @Override
    public String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_COAP;
    }
}
