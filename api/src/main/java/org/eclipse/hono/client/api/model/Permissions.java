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
package org.eclipse.hono.client.api.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Permissions implements Serializable {
    private final Set<Permission> values;

    public Permissions(final Permission... permissions) {
        this.values = new HashSet<>(Arrays.asList(permissions));
    }

    public boolean contains(Permission permission) {
        return values.contains(permission);
    }

    public void addPermissions(Permission... permissions) {

        values.addAll(Arrays.asList(permissions));
    }

    @Override
    public String toString()
    {
        return "Permissions{" +
           "values=" + values +
           '}';
    }

    @Override public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final Permissions that = (Permissions) o;

        return values.equals(that.values);

    }

    @Override public int hashCode() {
        return values.hashCode();
    }
}
