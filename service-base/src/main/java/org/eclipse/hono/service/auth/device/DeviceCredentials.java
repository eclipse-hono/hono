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

package org.eclipse.hono.service.auth.device;

import org.eclipse.hono.util.CredentialsObject;

/**
 * A wrapper around credentials provided by a device for authentication.
 *
 */
public interface DeviceCredentials {

    /**
     * Gets the type of credentials this instance represents.
     * 
     * @return The type.
     */
    String getType();

    /**
     * Gets the identity that the device wants to authenticate as.
     * 
     * @return The identity.
     */
    String getAuthId();

    /**
     * Gets the tenant that the device claims to belong to.
     * 
     * @return The tenant.
     */
    String getTenantId();

    /**
     * Verifies that the credentials provided by the device match the credentials
     * that are on record for the device.
     * 
     * @param credentialsOnRecord The credentials for the device as returned by the
     *                            <em>Credentials</em> API.
     * @return {@code true} if the credentials provided by the device have been validated successfully.
     * @throws IllegalArgumentException if the credentials on record do not contain any secrets.
     */
    boolean validate(CredentialsObject credentialsOnRecord);
}
