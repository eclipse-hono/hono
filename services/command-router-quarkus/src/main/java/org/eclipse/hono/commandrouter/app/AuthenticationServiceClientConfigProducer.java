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


package org.eclipse.hono.commandrouter.app;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientOptions;

import io.smallrye.config.ConfigMapping;

/**
 * Producers for configuring access to a Hono Authentication server.
 *
 */
@ApplicationScoped
public class AuthenticationServiceClientConfigProducer {

    @Produces
    @Singleton
    AuthenticationServerClientConfigProperties authenticationServerClientProperties(
            @ConfigMapping(prefix = "hono.auth")
            final AuthenticationServerClientOptions options) {
        final var props = new AuthenticationServerClientConfigProperties(options);
        props.setServerRoleIfUnknown("Authentication Server");
        return props;
    }
}
