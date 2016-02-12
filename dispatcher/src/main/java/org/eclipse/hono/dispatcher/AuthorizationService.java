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

import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.hono.client.api.model.Permission;
import org.eclipse.hono.client.api.model.TopicAcl;
import org.eclipse.hono.dispatcher.model.AccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuthorizationService implements IAuthorizationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

    // holds mapping topic -> acl
    private final ConcurrentMap<String, AccessControlList> topics = new ConcurrentHashMap<>();

    public AuthorizationService() {
    }

    @Override
    public Set<String> getAuthorizedSubjects(final String topic, final Permission permission) {
        return ofNullable(topics.get(topic)).map(list -> list.getAuthorizedSubjectsFor(permission)).orElse(emptySet());
    }

    @Override
    public boolean hasPermission(final String subject, final String topic, final Permission permission) {
        return ofNullable(topics.get(topic)).map(acl -> acl.hasPermission(subject, permission)).orElse(false);
    }

    @Override
    public boolean hasTopic(final String topic) {
        return topics.keySet().contains(topic);
    }

    @Override
    public void initialTopicAcls(final String topic, final TopicAcl topicAcl) {
        LOGGER.debug("Initialized topic {} with acls {}.", topic, topicAcl);
        topics.computeIfAbsent(topic, t -> new AccessControlList()).setAclEntry(topicAcl);
    }

    @Override
    public void updateTopicAcls(final String topic, final TopicAcl topicAcl) {
        LOGGER.debug("Updated topic {} with acls {}.", topic, topicAcl);
        topics.computeIfPresent(topic, (key, value) -> {
            value.setAclEntry(topicAcl);
            return value;
        });
    }
}
