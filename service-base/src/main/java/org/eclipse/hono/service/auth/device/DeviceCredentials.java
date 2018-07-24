/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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
