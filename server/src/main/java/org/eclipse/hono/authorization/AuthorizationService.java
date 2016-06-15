/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.authorization;

import java.util.Set;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Verticle;

/**
 * Provides methods to add, remove or retrieve permissions on a resource for a given subject.
 */
public interface AuthorizationService extends Verticle {

    /**
     * Checks a permission for a subject and resource.
     *
     * @param subject the authorization subject
     * @param resource the resource on which the subject want to be authorized
     * @param permission the requested permission
     * @return true if the subject has the requested permission on the given resource
     */
    boolean hasPermission(String subject, ResourceIdentifier resource, Permission permission);

    /**
     * Adds permission(s) for a subject/resource.
     *
     * @param subject the authorization subject
     * @param resource the resource for which to add a permission
     * @param first the permission to add
     * @param rest more permission to add optionally
     */
    void addPermission(String subject, ResourceIdentifier resource, final Permission first, final Permission... rest);

    /**
     * Adds permission(s) for a subject/resource.
     *
     * @param subject the authorization subject
     * @param resource the resource for which to add a permission
     * @param permissions set of permissions to add
     */
    void addPermission(String subject, ResourceIdentifier resource, final Set<Permission> permissions);

    /**
     * Removes permission(s) for a subject/resource.
     *
     * @param subject the authorization subject
     * @param resource the resource for which to remove a permission
     * @param first the permission to remove
     * @param rest more permission to remove optionally
     */
    void removePermission(String subject, ResourceIdentifier resource, final Permission first, final Permission... rest);
}
