/**
 * Copyright (c) 2016 Red Hat.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hono.adapter.rest.runtime.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.hono.adapter.rest.DefaultHttpRequestMapping;
import org.eclipse.hono.adapter.rest.HttpRequestMapping;
import org.eclipse.hono.adapter.rest.JacksonPayloadEncoder;
import org.eclipse.hono.adapter.rest.RestProtocolAdapter;

import java.util.Optional;

import static java.lang.Integer.parseInt;
import static org.apache.camel.component.amqp.AMQPComponent.amqp10Component;

public class RestProtocolAdapterConfiguration {

    public static void main(String... args) throws Exception {
        CamelContext camelContext = new DefaultCamelContext();

        // IoT Connector configuration
        String amqpBrokerUrl = property("AMQP_SERVICE_HOST", "localhost");
        int amqpBrokerPort = property("AMQP_SERVICE_PORT", 5672);
        camelContext.addComponent("amqp", amqp10Component("amqp://guest:guest@" + amqpBrokerUrl + ":" + amqpBrokerPort));

        // REST endpoint configuration
        String host = "0.0.0.0";
        int port = 8080;
        HttpRequestMapping requestMapping = new DefaultHttpRequestMapping(Optional.of(new JacksonPayloadEncoder()));
        RestProtocolAdapter protocolAdapter = new RestProtocolAdapter(requestMapping, host, port);
        camelContext.addRoutes(protocolAdapter);

        camelContext.start();
    }

    private static String property(String key, String defaultValue) {
        String property = System.getProperty(key);
        if(property != null) {
            return property;
        }
        property = System.getenv(key);
        if(property != null) {
            return property;
        }
        return defaultValue;
    }

    private static int property(String key, int defaultValue) {
        String property = property(key, null);
        return property == null ? defaultValue : parseInt(property);
    }

}