/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap.option;

import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.option.IntegerRangeOptionDefinition;

/**
 * CoAP custom time option.
 * <p>
 * Used in CoAP request to indicate that the client wants to get the servers system-time in milliseconds.
 * Any value in the option as part of a request is ignored.
 * <p>
 * If the option is present in a request, the server adds also a time option to the response with the
 * servers system-time in milliseconds. Also, a client can request this option be included in a response via the
 * {@value #QUERY_PARAMETER_NAME} request parameter.
 * <p>
 * This option uses the same option number as is used in the Californium cloud-demo-server application
 * (<a href="https://github.com/boaks/californium/blob/add_cloud_demo_server/demo-apps/cf-cloud-demo-server/src/main/java/org/eclipse/californium/cloud/option/TimeOption.java#L49">see here</a>).
 * TODO: update link once it's been merged into the eclipse-californium project.
 */
public final class TimeOption extends Option {

    /**
     * The COAP option number.
     * <p>
     * <b>NOTE:</b> this option number is in the "experimental" range and as such is not suitable for
     * interoperability with other CoAP implementations. This implementation should be changed if CoAP ever
     * defines its own official option number for reporting server time.
     * <p>
     * For further information and discussion, see:
     * <ul>
     *   <li> <a href="https://www.iana.org/assignments/core-parameters/core-parameters.xhtml#option-numbers">IANA CoAP Option Numbers</a> </li>
     *   <li> <a href="https://github.com/eclipse-californium/californium/issues/2134">Question about server time reporting in Californium</a> </li>
     *   <li> <a href="https://github.com/eclipse-hono/hono/issues/3502">Issue for adding a time option to Hono</a> </li>
     *   <li> <a href="https://github.com/eclipse-hono/hono/pull/3503">Pull request for adding a time option to Hono</a> </li>
     * </ul>
     */
    public static final int NUMBER = 0xfde8;

    /**
     * The request parameter name clients should use to request the server <em>time</em> be sent back as part of the
     * response (as a time option).
     */
    public static final String QUERY_PARAMETER_NAME = "hono-time";

    /**
     * This option's definition to be used with the Californium option registry.
     */
    public static final IntegerRangeOptionDefinition DEFINITION = new IntegerRangeOptionDefinition(NUMBER, "Server-Time", true, 0, Long.MAX_VALUE);

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
        super(DEFINITION, time);
    }
}
