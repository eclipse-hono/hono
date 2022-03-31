/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * An annotation which configures a test to run only if DNS rebinding for the nip.io domain works in the
 * local environment.
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfDnsRebindingIsSupportedCondition.class)
public @interface EnabledIfDnsRebindingIsSupported {

    /**
     * The default domain name.
     */
    String DEFAULT_DOMAIN = "nip.io";

    /**
     * The domain to use for checking DNS rebinding to work.
     * <p>
     * The default value of this property is {@value #DEFAULT_DOMAIN}.
     *
     * @return The domain name.
     */
    String domain() default DEFAULT_DOMAIN;
}
