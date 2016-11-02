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

package org.eclipse.hono.server;

import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around a {@code ProtonReceiver} that represents a Hono client sending data downstream.
 * <p>
 * The main purpose of this class to <em>attach</em> a (surrogate) identifier to the receiver.
 */
public class UpstreamReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(UpstreamReceiver.class);
    private ProtonReceiver link;
    private String id;

    /**
     * Creates a new instance for an identifier and a receiver link.
     * <p>
     * The receiver is configured for manual flow control and disposition handling.
     * 
     * @param linkId The identifier for the link.
     * @param receiver The link for receiving data from the client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public UpstreamReceiver(final String linkId, final ProtonReceiver receiver) {
        this.id = Objects.requireNonNull(linkId);
        this.link = Objects.requireNonNull(receiver);
        this.link.setAutoAccept(false).setPrefetch(0);
    }

    public void replenish(final int replenishedCredits) {
        LOG.debug("replenishing client [{}] with {} credits", id, replenishedCredits);
        link.flow(replenishedCredits);
    }

    public void drain(final long timeoutMillis, final Handler<AsyncResult<Void>> drainCompletionHandler) {
        LOG.debug("draining client [{}]", id);
        link.drain(timeoutMillis, drainCompletionHandler);
    }

    public int getCredit() {
        return link.getCredit() - link.getQueued();
    }

    public void close(final ErrorCondition error) {
        if (error != null) {
            link.setCondition(error);
        }
        link.close();
    }

    /**
     * @return the link
     */
    public ProtonReceiver getLink() {
        return link;
    }

    /**
     * @return the link ID
     */
    public String getLinkId() {
        return id;
    }

    /**
     * Gets the ID of the connection this link is running on.
     * 
     * @return The ID.
     */
    public String getConnectionId() {
        return Constants.getConnectionId(link);
    }
}