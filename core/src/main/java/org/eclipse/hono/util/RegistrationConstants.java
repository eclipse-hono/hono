/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

/**
 * Constants &amp; utility methods used throughout the Registration API.
 */
public final class RegistrationConstants extends RequestResponseApiConstants {

    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>assert device registration</em> operation.
     */
    public static final String ACTION_ASSERT = "assert";

    /**
     * The name of the field containing a device's registration information.
     * May be used when getting registration data in order to create the
     * response to the <em>assert device registration</em> operation (which
     * won't contain such a field though).
     */
    public static final String FIELD_DATA = "data";

    /**
     * The name of the field in a response to the <em>assert Device Registration</em> operation
     * that contains the identifiers of those gateways that may act on behalf of the device.
     */
    public static final String FIELD_VIA = "via";

    /**
     * The name of the downstream mapper used. This mapper should be configured for the adapter and can be referenced using
     * this field.
     */
    public static final String FIELD_DOWNSTREAM_MESSAGE_MAPPER = "downstream-message-mapper";

    /**
     * The name of the upstream mapper used. This mapper should be configured for the adapter and can be referenced using
     * this field.
     */
    public static final String FIELD_UPSTREAM_MESSAGE_MAPPER = "upstream-message-mapper";

    /**
     * The name of the Device Registration API endpoint.
     */
    public static final String REGISTRATION_ENDPOINT = "registration";


    /**
     * Top level property name of a command endpoint.
     */
    public static final String FIELD_COMMAND_ENDPOINT = "command-endpoint";

    /**
     * The name of the property containing the headers for a command endpoint.
     */
    public static final String FIELD_COMMAND_ENDPOINT_HEADERS = "headers";

    /**
     * The name of the property containing the uri format for a command endpoint.
     */
    public static final String FIELD_COMMAND_ENDPOINT_URI = "uri";

    /**
     * The name of the property containing the additional payload properties for a command endpoint.
     */
    public static final String FIELD_COMMAND_ENDPOINT_PAYLOAD_PROPERTIES = "payload-properties";

    private RegistrationConstants() {
        // prevent instantiation
    }
}
