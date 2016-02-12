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
package org.eclipse.hono.client.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.hono.client.api.model.Message;
import org.eclipse.hono.client.api.model.TopicAcl;

public interface ConnectorClient {
    /*
     * register client
     */
    // String registerClient();
    /**
     * create a topic and add acls to the topic
     */
    void registerTopic(final String clientId, final String topic, final TopicAcl topicAcl) throws IOException;

    /**
     * remove topic
     */
    void deregisterTopic(final String clientId);

    /*
     * send message to a topic
     */
    void sendMessage(final String topic, final Map<String, String> headers, ByteBuffer payload) throws IOException;

    /*
     * register a topic
     */
    void subscriptionForTopic(final String topic, final Consumer<Message> handler) throws IOException;

    /*
     * deregister a topic
     */
    void desubscriptionForTopic(final String topic) throws IOException;
}
