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

package org.eclipse.hono.service.auth.delegating;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * Configuration properties for the {@code DelegatingAuthenticationService}.
 *
 */
public class AuthenticationServerClientConfigProperties extends ClientConfigProperties {

    private final SignatureSupportingConfigProperties validation = new SignatureSupportingConfigProperties();

    /**
     * Gets the properties for determining key material for validating user tokens.
     * 
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return validation;
    }
}
