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
package org.eclipse.hono.dispatcher.amqp.configuration;

import java.util.HashMap;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

/**
 * Configuration class for AMQP queue/exchange configuration. Loads the following configuration:
 * 
 * <pre>
 * {
 *   "label":{
 *     "exchange" : {
 *       "name" : "exchange.name",
 *       "type" : "direct",      // default "direct"
 *       "durable" : true,       // default true
 *       "autoDelete" : false,   // default false
 *       "declare" : false       // default true
 *     },
 *     "queue" : {
 *       "name" : "queue.name",
 *       "durable" : true,       // default true
 *       "autoDelete" : false,   // default false
 *       "exclusive" : false,    // default false
 *       "declare" : false,      // default true
 *       "message-ttl" : 1000,   // default -1 (disabled), time in milliseconds after message times out
 *       "expiration" : 1000,    // default -1 (disabled), time in milliseconds after queue is removed if unused
 *       "max-length" : 1000     // default -1 (disabled), max length of queue
 *     },
 *     "binding" : ["routingKey", "anotherKey"]
 *   },
 *   "label2" :{ ... }
 *   ...
 * }
 * </pre>
 */
public final class QueueConfigurationLoader {
    /**
     * Name of the environment variable containing the queue configuration.
     */
    public static final String              QUEUE_CONFIG = "QUEUE_CONFIG";
    public static final String              DEFAULT_QUEUE_CONFIG = new StringBuilder()
                                                                         .append("{\"in\" : {")
                                                                         .append("\"exchange\" : {")
                                                                         .append("\"name\" : \"in\",")
                                                                         .append("\"declare\" : true")
                                                                         .append("},")
                                                                         .append("\"queue\" : {")
                                                                         .append("\"name\" : \"queue.in\"")
                                                                         .append("},")
                                                                         .append("\"binding\" : [\"message\",\"registerTopic\"]")
                                                                         .append("},")
                                                                         .append("\"out\" : {")
                                                                         .append("\"exchange\" : {")
                                                                         .append("\"name\" : \"out\",")
                                                                         .append("\"declare\" : true")
                                                                         .append("}")
                                                                         .append("}}")
                                                                         .toString();
    private Map<String, QueueConfiguration> configs      = new HashMap<>();

    private QueueConfigurationLoader(final Map<String, QueueConfiguration> configs) {
        this.configs = configs;
    }

    /**
     * Resolves the queue configuration from the environment.
     *
     * @return the resolved configuration.
     */
    public static QueueConfigurationLoader fromEnv() {
        return QueueConfigurationLoader.fromEnv(new DefaultEnvironment());
    }

    /**
     * Resolves the queue configuration from the provided environment.
     *
     * @param env the environment.
     * @return the resolved configuration.
     */
    public static QueueConfigurationLoader fromEnv(final Environment env) {
        final Map<String, QueueConfiguration> configs = new HashMap<>();
        String queueConfig = env.get(QueueConfigurationLoader.QUEUE_CONFIG);
        if (queueConfig == null) {
            queueConfig = DEFAULT_QUEUE_CONFIG;
        }
        final JsonObject json = Json.parse(queueConfig).asObject();
        json.forEach(m -> configs.put(m.getName(), QueueConfiguration.parse(m.getValue().asObject())));
        return new QueueConfigurationLoader(configs);
    }

    /**
     * Get Config for given label.
     *
     * @param name label of a queue configuration
     * @return Config for the given label.
     */
    public QueueConfiguration getConfig(final String name) {
        return configs.get(name);
    }
}
