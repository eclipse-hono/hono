/**
 * Copyright (c) 2016, 2017 Red Hat and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;

public class KeepAliveChecker {

    private final Vertx vertx;
    private final MqttEndpoint endpoint;

    private int timeout;
    private long timer;

    /**
     * Constructor
     *
     * @param vertx Vert.x instance
     * @param endpoint  MQTT remote endpoint
     */
    public KeepAliveChecker(Vertx vertx, MqttEndpoint endpoint) {

        this.vertx = vertx;
        this.endpoint = endpoint;

        // keeps the connection alive when PINGREQ comes
        this.endpoint.pingHandler(v -> {
           this.keepAlive();
        });

        // waiting for one and a half times the keep alive time period (MQTT spec)
        this.timeout = (this.endpoint.keepAliveTimeSeconds() +
                this.endpoint.keepAliveTimeSeconds() / 2) * 1000;

        this.setTimer();
    }

    /**
     * Keeps the connection alive
     */
    public void keepAlive() {

        this.vertx.cancelTimer(this.timer);
        this.setTimer();
    }

    /**
     * Close the checker, closing the timer
     */
    public void close() {

        this.vertx.cancelTimer(this.timer);
    }

    /**
     * Start the timer
     */
    private void setTimer() {

        this.timer = this.vertx.setTimer(this.timeout, t -> {
            this.endpoint.close();
        });
    }
}
