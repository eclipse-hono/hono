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

import org.eclipse.hono.adapter.AdapterConfig;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.HonoClientConfigProperties;
import org.eclipse.leshan.server.californium.CaliforniumRegistrationStore;
import org.eclipse.leshan.server.californium.impl.InMemoryRegistrationStore;
import org.eclipse.leshan.server.impl.InMemorySecurityStore;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.model.StandardModelProvider;
import org.eclipse.leshan.server.security.EditableSecurityStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for the LWM2M protocol adapter.
 */
@Configuration
public class ApplicationConfig extends AdapterConfig {

    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    @Profile("!standalone")
    public HonoClientConfigProperties honoClientConfig() {
        return new HonoClientConfigProperties();
    }

    @Bean
    @Profile("!standalone")
    public HonoClient honoClient(final HonoClientConfigProperties config) {
        return new HonoClient(getVertx(), honoConnectionFactory());
    }

    @Bean
    @Profile("!standalone")
    @Qualifier("endpointMap")
    public Map<String, String> endpointToHonoIdMap() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public CaliforniumRegistrationStore defaultCaliforniumRegistrationStore() {
        return new InMemoryRegistrationStore();
    }

    @Bean
    public LwM2mModelProvider defaultModelProvier() {
        return new StandardModelProvider();
    }

    @Bean
    @Profile("standalone")
    public EditableSecurityStore defaultSecurityStore(@Autowired final ServerKeyProvider keyProvider) {
        return new InMemorySecurityStore();
    }
}
