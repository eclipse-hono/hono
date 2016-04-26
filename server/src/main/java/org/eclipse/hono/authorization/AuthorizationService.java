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

public interface AuthorizationService
{

   Set<String> getAuthorizedSubjects(String resource, Permission permission);

   boolean hasPermission(String subject, String resource, Permission permission);

   void addPermission(String subject, String resource, final Permission first, final Permission... rest);

   void removePermission(String subject, String resource, final Permission first, final Permission... rest);
}
