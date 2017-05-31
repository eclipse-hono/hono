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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.hono.auth.Activity;

/**
 * Holds a list of @{@link AclEntry}s.
 */
public class AccessControlList
{
    private final Map<String, AclEntry> entries = new HashMap<>();

    public AccessControlList(final AclEntry... aclEntries) {
        Stream.of(aclEntries).forEach(e -> entries.put(e.getAuthSubject(), e));
    }

    public AclEntry getAclEntry(final String subject) {
        return entries.get(subject);
    }

    public boolean hasPermission(final String subject, final Activity permission) {
        return Optional.ofNullable(entries.get(subject)).map(entry -> entry.getPermissions().contains(permission))
                .orElse(false);
    }

    public void setAclEntry(final AclEntry aclEntry) {
        entries.put(aclEntry.getAuthSubject(), aclEntry);
    }

    @Override
    public String toString() {
        return "AccessControlList{" + "entries=" + entries + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final AccessControlList that = (AccessControlList) o;

        return entries.equals(that.entries);

    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }
}
