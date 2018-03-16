/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonReceiver;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;

/**
 * Abstract client for consuming messages from a Hono server.
 */
abstract class AbstractConsumer extends AbstractHonoClient implements MessageConsumer {

    AbstractConsumer(final Context context, final ClientConfigProperties config, final ProtonReceiver receiver) {
        super(context, config);
        this.receiver = receiver;
    }

    @Override
    public void flow(final int credits) throws IllegalStateException {
        receiver.flow(credits);
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeLinks(closeHandler);
    }

}
