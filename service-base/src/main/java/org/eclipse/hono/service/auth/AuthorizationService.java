/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.auth;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;

/**
 * A service for authorizing access to Hono resources.
 * <p>
 * Resources can be API endpoints and operations.
 */
public interface AuthorizationService {

    /**
     * Checks if a user is authorized to perform an activity on a given resource.
     *
     * @param user The user to check authorization for.
     * @param resource The resource to authorize access to.
     * @param intent The activity to authorize.
     * @return A future indicating the outcome of the check.
     *         The future will succeed if the service invocation has been successful.
     *         The boolean contained indicates whether the user is authorized to perform the
     *         activity on the resource.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Boolean> isAuthorized(HonoUser user, ResourceIdentifier resource, Activity intent);

    /**
     * Checks if a user is authorized to execute an API operation on a particular resource.
     *
     * @param user The user to check authorization for.
     * @param resource The resource that is subject to the operation to authorize.
     * @param operation The operation to authorize.
     * @return A future indicating the outcome of the check.
     *         The future will succeed if the service invocation has been successful.
     *         The boolean contained indicates whether the user is authorized to execute the
     *         operation on the resource.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Boolean> isAuthorized(HonoUser user, ResourceIdentifier resource, String operation);
}
