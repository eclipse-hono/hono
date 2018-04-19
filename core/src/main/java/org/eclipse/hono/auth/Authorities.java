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

package org.eclipse.hono.auth;

import java.util.Map;

import org.eclipse.hono.util.ResourceIdentifier;

/**
 * A collection of authorities granted on resources and/or operations.
 *
 */
public interface Authorities {

    /**
     * Checks if these authorities include claims allowing an intended activity on a resource.
     * 
     * @param resourceId The resource.
     * @param intent The intended activity on the resource
     * @return {@code true} if the activity is allowed.
     */
    boolean isAuthorized(ResourceIdentifier resourceId, Activity intent);

    /**
     * Checks if these authorities include claims allowing execution of an operation of a resource.
     * 
     * @param resourceId The resource.
     * @param operation The operation to execute.
     * @return {@code true} if execution is allowed.
     */
    boolean isAuthorized(ResourceIdentifier resourceId, String operation);

    /**
     * Gets the authorities as a map of claims.
     * 
     * @return The claims.
     */
    Map<String, Object> asMap();
}
