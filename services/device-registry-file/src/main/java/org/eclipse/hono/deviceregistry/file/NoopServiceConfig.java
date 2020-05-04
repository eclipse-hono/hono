/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.file;

import org.eclipse.hono.deviceregistry.service.credentials.NoopCredentialsService;
import org.eclipse.hono.deviceregistry.service.device.NoopRegistrationService;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantService;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.TenantService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for the dummy implementation of Hono's AMQP 1.0 based
 * registry endpoints.
 *
 */
@Configuration
@ConditionalOnProperty(name = "hono.app.type", havingValue = "dummy")
public class NoopServiceConfig {

    /**
     * Creates an instance of the dummy service for managing tenant information.
     * 
     * @return The service.
     */
    @Bean
    public TenantService tenantService() {
        return new NoopTenantService();
    }

    /**
     * Creates an instance of the dummy service for managing device registration information.
     * 
     * @return The service.
     */
    @Bean
    public RegistrationService registrationService() {
        return new NoopRegistrationService();
    }

    /**
     * Creates an instance of the dummy service for managing device credentials.
     * 
     * @return The service.
     */
    @Bean
    public CredentialsService credentialsService() {
        return new NoopCredentialsService();
    }
}
