/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.auth.delegating.quarkus;

import io.quarkus.arc.config.ConfigProperties;

/**
 * Standard {@link org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties} which can be
 * bound to environment variables by Quarkus.
 *
 */
@ConfigProperties(prefix = "hono.auth", namingStrategy = ConfigProperties.NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class AuthenticationServerClientConfigProperties
        extends org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties {

    /**
     * Standard {@link SignatureSupportingConfigProperties} which can be bound to configuration properties by Quarkus.
     *
     */
    public static class SignatureSupportingConfigProperties extends org.eclipse.hono.config.SignatureSupportingConfigProperties {
    }
}
