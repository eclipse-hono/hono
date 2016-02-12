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

import java.util.Optional;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;

/**
 * ...
 */
public final class HeaderReader {
    public static final String TOPIC_KEY   = "topic";
    public static final String SUBJECT_KEY = "subject";

    public static String getTopic(final AMQP.BasicProperties properties) {
        return Optional.ofNullable(properties.getHeaders().get(HeaderReader.TOPIC_KEY))
                .map(o -> (LongString) o)
                .map(ls -> ls.toString())
                .orElse(null);
    }

    public static String getAuthorizationSubject(final AMQP.BasicProperties properties) {
        return Optional.ofNullable(properties.getHeaders().get(HeaderReader.SUBJECT_KEY))
                .map(o -> (LongString) o)
                .map(ls -> ls.toString())
                .orElse(null);
    }
}
