/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import io.opentracing.tag.StringTag;

/**
 * Constants used by the CoAP adapter.
 *
 */
public abstract class CoapConstants {

    /**
     * A tag to use for keeping track of a CoAP message type.
     */
    public static final StringTag TAG_COAP_MESSAGE_TYPE = new StringTag("coap.message_type");
    /**
     * A tag to use for keeping track of a CoAP response code.
     */
    public static final StringTag TAG_COAP_RESPONSE_CODE = new StringTag("coap.response_code");

    /**
     * The name of the property that contains the maximum number of milliseconds to wait for an
     * upstream command before responding with an empty ACK message to a client's request.
     *
     * @see CoapAdapterProperties#setTimeoutToAck(int)
     */
    public static final String TIMEOUT_TO_ACK = "timeoutToAck";

    /**
     * The name of the property that contains the maximum number of milliseconds to wait for an upstream command before
     * responding with an empty ACK message to a client's request using the uri-query parameter "piggy".
     *
     * @see CoapAdapterProperties#setTimeoutToAck(int)
     */
    public static final String DEVICE_TRIGGERED_TIMEOUT_TO_ACK = "deviceTriggeredTimeoutToAck";

    /**
     * The header name defined for clients requesting the server <em>time</em> be sent back as part of the response.
     */
    public static final String HEADER_SERVER_TIME_IN_RESPONSE = "hono-time";

    /**
     * The COAP option number for the "time option" that reports the server time back as part of the COAP response.
     * This can be used in COAP requests (to request that the server provides its time in the corresponding response)
     * as well as in the response (to convey the actual value of the server time).
     */
    public static final int TIME_OPTION_NUMBER = 0xff20; // TODO: figure out the "correct" Option number here.

    private CoapConstants() {
        // prevent instantiation
    }
}
