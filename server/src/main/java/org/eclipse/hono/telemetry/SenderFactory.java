/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.telemetry;

import io.vertx.core.Future;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;

/**
 * A factory for creating {@code ProtonSender} instances.
 */
public interface SenderFactory {

    /**
     * Creates a new sender from a connection and a target address.
     * 
     * @param connection the connection to create the sender from.
     * @param address the target address to use for the sender.
     * @param result the future to notify about the outcome of the creation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    void createSender(ProtonConnection connection, String address, Future<ProtonSender> result);
}
