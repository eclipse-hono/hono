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

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * HonoServerConfigProperties
 *
 */
public class HonoServerConfigProperties extends ServiceConfigProperties {

    private SignatureSupportingConfigProperties registrationAssertionProperties;

    public SignatureSupportingConfigProperties getRegistrationAssertion() {
        return registrationAssertionProperties;
    }

    public final void setRegistrationAssertion(final SignatureSupportingConfigProperties signingProps) {
        this.registrationAssertionProperties = Objects.requireNonNull(signingProps);
    }
}
