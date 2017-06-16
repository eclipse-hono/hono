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

package org.eclipse.hono.server;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * Configuration properties for a Hono server.
 *
 */
public class HonoServerConfigProperties extends ServiceConfigProperties {

    private final SignatureSupportingConfigProperties registrationAssertionProperties = new SignatureSupportingConfigProperties();

    /**
     * Gets the properties for determining key material for validating registration assertion tokens.
     * 
     * @return The properties.
     */
    public SignatureSupportingConfigProperties getValidation() {
        return registrationAssertionProperties;
    }
}
