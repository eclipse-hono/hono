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

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Wraps an authorization subject and a set of {@link Permission}s.
 */
public class AclEntry
{
    private final String authSubject;
    private final Set<Permission> permissions;

    public AclEntry(final String authSubject, final Permission... permissions)
    {
        this.authSubject = requireNonNull(authSubject);
        this.permissions = EnumSet.copyOf(Arrays.asList(permissions));
    }

    public AclEntry(final String authSubject, final Set<Permission> permissions)
    {
        this.authSubject = requireNonNull(authSubject);
        this.permissions = EnumSet.copyOf(permissions);
    }

    public String getAuthSubject()
    {
        return authSubject;
    }

    public Set<Permission> getPermissions()
    {
        return permissions;
    }

    @Override
    public String toString()
    {
        return "AclEntry{" + "authSubject='" + authSubject + '\'' + ", permissions=" + permissions + '}';
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final AclEntry aclEntry = (AclEntry) o;

        if (!authSubject.equals(aclEntry.authSubject))
            return false;
        return permissions.equals(aclEntry.permissions);

    }

    @Override
    public int hashCode()
    {
        int result = authSubject.hashCode();
        result = 31 * result + permissions.hashCode();
        return result;
    }
}
