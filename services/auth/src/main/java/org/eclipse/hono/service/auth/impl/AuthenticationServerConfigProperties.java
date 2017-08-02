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

package org.eclipse.hono.service.auth.impl;

import java.util.Objects;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;


/**
 * Configuration properties for the {@code FileBasedAuthenticationService}.
 *
 */
public class AuthenticationServerConfigProperties {

    private static final Resource DEFAULT_PERMISSIONS_RESOURCE = new ClassPathResource("permissions.json");
    private final SignatureSupportingConfigProperties signing = new SignatureSupportingConfigProperties();
    private Resource permissionsResource = DEFAULT_PERMISSIONS_RESOURCE;

    /**
     * Gets the properties for determining key material for creating tokens.
     * 
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getSigning() {
        return signing;
    }

    /**
     * Gets the properties for determining key material for validating tokens issued by this service.
     * 
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return signing;
    }

    /**
     * Get the resource that the authorization rules should be loaded from.
     * <p>
     * If not set the default permissions will be loaded from <em>classpath:permissions.json</em>.
     * 
     * @return The resource.
     */
    public final Resource getPermissionsPath() {
        return permissionsResource;
    }

    /**
     * Set the resource that the authorization rules should be loaded from.
     * <p>
     * If not set the default permissions will be loaded from <em>classpath:permissions.json</em>.
     * 
     * @param permissionsResource The resource.
     * @throws NullPointerException if the resource is {@code null}.
     */
    public final void setPermissionsPath(final Resource permissionsResource) {
        this.permissionsResource = Objects.requireNonNull(permissionsResource);
    }
}
