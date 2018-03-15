/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.util.Arrays;
import java.util.List;

/**
 * Constants &amp; utility methods used throughout the Registration API.
 */
public final class RegistrationConstants extends RequestResponseApiConstants {

    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>assert device registration</em> operation.
     */
    public static final String ACTION_ASSERT     = "assert";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>register device</em> operation.
     */
    public static final String ACTION_REGISTER   = "register";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>get registration information</em> operation.
     */
    public static final String ACTION_GET        = "get";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>deregister device</em> operation.
     */
    public static final String ACTION_DEREGISTER = "deregister";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>update device registration</em> operation.
     */
    public static final String ACTION_UPDATE     = "update";

    /**
     * The name of the field in a response to the <em>assert device registration</em> operation
     * that contains the registration status assertion.
     */
    public static final String FIELD_ASSERTION    = "assertion";
    /**
     * The name of the field in a response to the <em>get registration information</em> operation
     * that contains a device's registration information.
     */
    public static final String FIELD_DATA         = "data";
    /**
     * The name of the field in a device's registration information that contains
     * <em>defaults</em> to be used by protocol adapters when processing messages published
     * by the device.
     */
    public static final String FIELD_DEFAULTS     = "defaults";

    /**
     * The name of the Device Registration API endpoint.
     */
    public static final String REGISTRATION_ENDPOINT = "registration";

    private static final List<String> ACTIONS = Arrays.asList(ACTION_ASSERT, ACTION_REGISTER,
            ACTION_GET, ACTION_DEREGISTER, ACTION_UPDATE);

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_REGISTRATION_IN = "registration.in";


    private RegistrationConstants() {
        // prevent instantiation
    }

    /**
     * Checks if a given string represents a valid action.
     * 
     * @param action The string to check.
     * @return {@code true} if the given string is a supported action.
     */
    public static boolean isValidAction(final String action) {
        if (action == null) {
            return false;
        } else {
            return ACTIONS.contains(action);
        }
    }
}
