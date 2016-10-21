/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.lwm2m;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClientConfigProperties;
import org.eclipse.leshan.server.client.ClientRegistry;
import org.eclipse.leshan.server.impl.ClientRegistryImpl;
import org.eclipse.leshan.server.impl.SecurityRegistryImpl;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.eclipse.leshan.server.security.SecurityRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vertx.core.Vertx;

/**
 * Configuration for the LWM2M protocol adapter.
 */
@Configuration
public class ApplicationConfig {

    private final Vertx vertx = Vertx.vertx();

    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    @Profile("!standalone")
    public HonoClientConfigProperties honoClientConfig() {
        return new HonoClientConfigProperties();
    }

    @Bean
    @Profile("!standalone")
    public HonoClient honoClient(final HonoClientConfigProperties config) {
        return new HonoClient(vertx, config);
    }

    @Bean
    @Profile("!standalone")
    @Qualifier("endpointMap")
    public Map<String, String> endpointToHonoIdMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ClientRegistry defaultClientRegistry() {
        return new ClientRegistryImpl();
    }

    @Bean
    public LwM2mModelProvider defaultModelProvier() {
        return new StandardModelProvider();
    }

    @Bean
    @Profile("standalone")
    public SecurityRegistry defaultSecurityRegistry(@Autowired final ServerKeyProvider keyProvider) {
        return new SecurityRegistryImpl(keyProvider.getServerPrivateKey(), keyProvider.getServerPublicKey());
    }
}
