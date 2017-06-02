/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.service.auth;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Verticle;

/**
 * A service for authorizing access to Hono resources.
 * <p>
 * Resources can be API endpoints and operations.
 */
public interface AuthorizationService extends Verticle {

    /**
     * Checks if a subject is authorized to perform an activity on a given resource.
     *
     * @param subject The subject to check authorization for.
     * @param resource The resource to authorize access to.
     * @param intent The activity to authorize.
     * @return {@code true} if the subject is authorized to perform the activity on the resource.
     */
    boolean hasPermission(String subject, ResourceIdentifier resource, Activity intent);

    /**
     * Checks if a subject is authorized to execute an API operation on a particular resource.
     *
     * @param subject The subject to check authorization for.
     * @param resource The resource that is subject to the operation to authorize.
     * @param operation The operation to authorize.
     * @return {@code true} if the subject is authorized to execute the operation on the resource.
     */
    boolean hasPermission(String subject, ResourceIdentifier resource, String operation);
}
