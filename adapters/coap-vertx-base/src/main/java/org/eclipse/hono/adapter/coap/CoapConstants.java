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


package org.eclipse.hono.adapter.coap;


/**
 * Constants used by the CoAP adapter.
 *
 */
public abstract class CoapConstants {

    /**
     * The name of the property that contains the maximum number of milliseconds to wait for an
     * upstream command before responding with an empty ACK message to a client's request.
     *
     * @see CoapAdapterProperties#setTimeoutToAck(int)
     */
    public static final String TIMEOUT_TO_ACK = "timeoutToAck";


    private CoapConstants() {
        // prevent instantiation
    }
}
