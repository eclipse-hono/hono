/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.authorization.impl;

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.hono.authorization.AccessControlList;
import org.eclipse.hono.authorization.AclEntry;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of AuthorizationService that holds acl data in memory i.e. no persistent storage.
 */
@Service
public final class InMemoryAuthorizationService extends BaseAuthorizationService
{

   private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryAuthorizationService.class);

   // holds mapping resource -> acl
   private final ConcurrentMap<String, AccessControlList> resources = new ConcurrentHashMap<>();

   public InMemoryAuthorizationService()
   {
      // allow default subject SEND permission on telemetry endpoint of default tenant
      addPermission(Constants.DEFAULT_SUBJECT, "telemetry/" + Constants.DEFAULT_TENANT, Permission.SEND);
   }

   @Override
   public Set<String> getAuthorizedSubjects(final String resource, final Permission permission)
   {
      return ofNullable(resources.get(resource)).map(acl -> acl.getAuthorizedSubjectsFor(permission))
         .orElse(emptySet());
   }

   @Override
   public boolean hasPermission(final String subject, final String resource, final Permission permission)
   {
      return ofNullable(resources.get(resource)).map(acl -> acl.hasPermission(subject, permission)).orElse(false);
   }

   @Override
   public void addPermission(final String subject, final String resource, final Permission first,
      final Permission... rest)
   {
      final EnumSet<Permission> permissions = EnumSet.of(first, rest);
      LOGGER.debug("Add permission {} for subject {} on resource {}.", permissions, subject, resource);
      resources.computeIfAbsent(resource, key -> new AccessControlList())
         .setAclEntry(new AclEntry(subject, permissions));
   }

   @Override
   public void removePermission(final String subject, final String resource, final Permission first,
      final Permission... rest)
   {
      final EnumSet<Permission> permissions = EnumSet.of(first, rest);
      LOGGER.debug("Delete permission {} for subject {} on resource {}.", first, subject, resource);
      resources.computeIfPresent(resource, (key, value) -> {
         ofNullable(value.getAclEntry(subject))
                 .map(AclEntry::getPermissions)
                 .ifPresent(p -> p.removeAll(permissions));
         return value;
      });
   }
}
