/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.coap.option;

import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;

/**
 * CoAP custom time option.
 * <p/>
 * Used in {@link Request} to indicate that the client wants to get the servers system-time in milliseconds.
 * Any value in the option as part of a request is ignored.
 * <p/>
 * If the option is present in a request, the server adds also a {@link TimeOption} to the {@link Response} with the
 * server's system-time in milliseconds. Also, a client can request this option be included in a response via a
 * {@link Request} parameter, "hono-time".
 *
 * @since 2.4.0
 */
public class TimeOption extends Option {

    /**
     * The COAP option number.
     * <p/>
     * <b>NOTE:</b> this option number is in the "experimental" range and as such is not suitable for
     * interoperability with other CoAP implementations. This implementation should be changed if CoAP ever
     * defines its own official option number for reporting server time.
     * <p/>
     * For further information and discussion, see:
     * <ul>
     *   <li> <a href="https://www.iana.org/assignments/core-parameters/core-parameters.xhtml#option-numbers">IANA CoAP Option Numbers</a> </li>
     *   <li> <a href="https://github.com/eclipse-californium/californium/issues/2134">Question about server time reporting in Californium</a> </li>
     *   <li> <a href="https://github.com/eclipse-hono/hono/issues/3502">Issue for adding a time option to Hono</a> </li>
     *   <li> <a href="https://github.com/eclipse-hono/hono/pull/3503">Pull request for adding a time option to Hono</a> </li>
     * </ul>
     */
    public static final int COAP_OPTION_TIME_NUMBER = 0xfde8;

    /**
     * The request parameter name clients should use to request the server <em>time</em> be sent back as part of the
     * response (as a time option).
     */
    public static final String COAP_OPTION_TIME_REQUEST_QUERY_PARAMETER_NAME = "hono-time";

    /**
     * Create time option with current system time.
     */
    public TimeOption() {
        this(System.currentTimeMillis());
    }

    /**
     * Create time option.
     *
     * @param time time in system milliseconds.
     */
    public TimeOption(final long time) {
        super(COAP_OPTION_TIME_NUMBER, time);
    }

    /**
     * Create time option.
     *
     * @param value time in system milliseconds as byte array.
     */
    public TimeOption(final byte[] value) {
        super(COAP_OPTION_TIME_NUMBER, value);
    }
}
