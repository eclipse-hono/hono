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

package org.eclipse.hono.deviceregistry;

import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


/**
 * Configuration properties for the device registry as own server.
 *
 */
public final class DeviceRegistryConfigProperties extends ServiceConfigProperties {

    private static final Resource DEFAULT_CREDENTIALS_RESOURCE = new ClassPathResource("credentials.json");
    private Resource credentialsResource = DEFAULT_CREDENTIALS_RESOURCE;
    private boolean saveToFile;


    /**
     * Get the resource that the credentials should be loaded from.
     * <p>
     * If not set the default credentials will be loaded from <em>classpath:credentials.json</em>.
     * 
     * @return The resource.
     */
    public final Resource getCredentialsPath() {
        return credentialsResource;
    }

    /**
     * Set the resource that the credentials should be loaded from.
     * <p>
     * If not set the default credentials will be loaded from <em>classpath:credentials.json</em>.
     * 
     * @param credentialsResource The resource.
     * @throws NullPointerException if the resource is {@code null}.
     */
    public final void setCredentialsPath(final Resource credentialsResource) {
        this.credentialsResource = Objects.requireNonNull(credentialsResource);
    }

    public boolean isSaveToFile() {
        return saveToFile;
    }

    public void setSaveToFile(final boolean saveToFile) {
        this.saveToFile = saveToFile;
    }
}
