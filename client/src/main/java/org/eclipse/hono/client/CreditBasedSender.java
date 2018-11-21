/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.client;

import io.vertx.core.Handler;

/**
 *  A client with methods to retrieve flow credits and also to set queueDrainHandler.
 *
 */
public interface CreditBasedSender {
    /**
     * Gets the number of messages this sender can send based on its current number of credits.
     * <p>
     * Note that the value returned is valid during execution of the current vert.x handler only.
     *
     * @return The number of messages.
     */
    int getCredit();

    /**
     * Sets a handler to be notified once this sender has capacity available to send a message.
     * <p>
     * The handler registered using this method will be invoked <em>exactly once</em> when this sender is replenished
     * with more credit from the peer. For subsequent notifications to be received, a new handler must be registered.
     * <p>
     * Client code can use this method to register a handler after it has checked this sender's capacity to send
     * messages using {@link #getCredit()}, e.g.
     *
     * <pre>
     * MessageSender sender;
     * ...
     *
     * if (sender.getCredit() &lt;= 0) {
     *     sender.sendQueueDrainHandler(replenished -&gt; {
     *         sender.send(msg);
     *     });
     * } else {
     *     sender.send(msg);
     * }
     * </pre>
     * <p>
     * Note that all the <em>send</em> methods fail if no credit is available.
     * 
     * @param handler The handler to invoke when this sender has been replenished with credit.
     * @throws IllegalStateException if there already is a handler registered. Note that this means that this sender is
     *             already waiting for credit.
     */
    void sendQueueDrainHandler(Handler<Void> handler);
}
