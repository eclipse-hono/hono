/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.messaging;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * Configuration properties for a Hono server.
 *
 */
public class HonoMessagingConfigProperties extends ServiceConfigProperties {

    /**
     * The default number of bytes that can be buffered unsettled per session created by a client.
     */
    public static final int DEFAULT_MAX_SESSION_WINDOW = 300 * 32 * 1024; // 300 frames of 32kb each

    private final SignatureSupportingConfigProperties registrationAssertionProperties = new SignatureSupportingConfigProperties();
    private int maxSessionWindow = DEFAULT_MAX_SESSION_WINDOW;

    /**
     * Gets the properties for determining key material for validating registration assertion tokens.
     * 
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return registrationAssertionProperties;
    }

    /**
     * Gets the maximum number of bytes that can be buffered unsettled per session created by a client.
     * <p>
     * This value is relevant for sessions created by clients only.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_SESSION_WINDOW}.
     * 
     * @return The maximum session window size.
     */
    public final int getMaxSessionWindow() {
        return maxSessionWindow;
    }

    /**
     * Sets the maximum number of bytes that can be buffered unsettled per session created by a client.
     * <p>
     * This value is relevant for sessions created by clients only.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_SESSION_WINDOW}.
     * 
     * @param maxSessionWindowSize The maximum session window size.
     */
    public final void setMaxSessionWindow(int maxSessionWindowSize) {
        this.maxSessionWindow = maxSessionWindowSize;
    }
}
