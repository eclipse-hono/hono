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
package org.eclipse.hono.dispatcher.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.hono.client.api.model.Permission;
import org.eclipse.hono.client.api.model.TopicAcl;

public class AccessControlList {
    private final Map<String, TopicAcl> entries = new HashMap<>();

    public AccessControlList(final TopicAcl... aclEntries) {
        Stream.of(aclEntries).forEach(e -> entries.put(e.getAuthSubject(), e));
    }

    public boolean hasPermission(final String subject, final Permission permission) {
        return Optional.ofNullable(entries.get(subject))
                .map(entry -> entry.getPermissions().contains(permission))
                .orElse(false);
    }

    public Set<String> getAuthorizedSubjectsFor(final Permission permission) {
        return entries.values()
                .stream()
                .filter(e -> e.getPermissions().contains(permission))
                .map(e -> e.getAuthSubject())
                .collect(Collectors.toSet());
    }

    public void setAclEntry(final TopicAcl aclEntry) {
        entries.put(aclEntry.getAuthSubject(), aclEntry);
    }

    @Override
    public String toString()
    {
        return "AccessControlList{" +
           "entries=" + entries +
           '}';
    }

    @Override public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final AccessControlList that = (AccessControlList) o;

        return entries.equals(that.entries);

    }

    @Override public int hashCode() {
        return entries.hashCode();
    }
}
