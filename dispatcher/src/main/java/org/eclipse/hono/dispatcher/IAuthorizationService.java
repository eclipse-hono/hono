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
package org.eclipse.hono.dispatcher;

import java.util.Set;

import org.eclipse.hono.client.api.model.Permission;
import org.eclipse.hono.client.api.model.TopicAcl;

public interface IAuthorizationService {
    Set<String> getAuthorizedSubjects(final String topic, final Permission permission);

    boolean hasPermission(String subject, final String topic, final Permission permission);

    boolean hasTopic(String topic);

    void initialTopicAcls(final String topic, final TopicAcl topicAcl);

    void updateTopicAcls(final String topic, final TopicAcl topicAcl);
}
