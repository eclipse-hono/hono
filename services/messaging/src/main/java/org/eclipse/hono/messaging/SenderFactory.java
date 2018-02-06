/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.messaging;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A factory for creating {@code ProtonSender} instances.
 */
public interface SenderFactory {

    /**
     * Creates a new sender from a connection and a target address.
     * <p>
     * The sender created will be open and ready to use.
     * 
     * @param connection The connection to create the sender from.
     * @param address The target address to use for the sender.
     * @param qos The quality of service the sender should use.
     * @param sendQueueDrainHandler The handler to notify about credits the sender is getting replenished with.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @return The outcome of the creation attempt.
     * @throws NullPointerException if any of connection, address or QoS is {@code null}.
     */
    Future<ProtonSender> createSender(
            ProtonConnection connection,
            ResourceIdentifier address,
            ProtonQoS qos,
            Handler<ProtonSender> sendQueueDrainHandler,
            Handler<Void> closeHook);
}
