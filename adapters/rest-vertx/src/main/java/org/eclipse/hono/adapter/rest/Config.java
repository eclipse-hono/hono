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

package org.eclipse.hono.adapter.rest;

import org.eclipse.hono.adapter.AdapterConfig;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class
 */
@Configuration
public class Config extends AdapterConfig {

    @Bean
    public ServiceLocatorFactoryBean serviceLocator() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(RestAdapterFactory.class);
        return bean;
    }
}
