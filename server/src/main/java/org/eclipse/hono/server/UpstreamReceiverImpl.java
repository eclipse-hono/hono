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
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A decorator for a {@code ProtonReceiver} that represents a Hono client sending data downstream.
 * <p>
 * The main purpose of this class is to <em>attach</em> a (surrogate) {@linkplain #getLinkId() identifier}
 * to the receiver.
 */
public class UpstreamReceiverImpl implements UpstreamReceiver {

    private static final int DEFAULT_LINK_CREDIT = 20;
    private static final Logger LOG = LoggerFactory.getLogger(UpstreamReceiverImpl.class);
    private ProtonReceiver link;
    private String id;

    UpstreamReceiverImpl(final String linkId, final ProtonReceiver receiver, final ProtonQoS qos) {
        this.id = Objects.requireNonNull(linkId);
        this.link = Objects.requireNonNull(receiver);
        this.link.setAutoAccept(false).setPrefetch(0).setQoS(qos);
    }

    @Override
    public void replenish(final int downstreamCredit) {

        int remainingCredit = link.getCredit();
        int maxCredit = Math.min(DEFAULT_LINK_CREDIT, downstreamCredit);
        int credit = maxCredit - remainingCredit;
        if (link.getCredit() <= 0) {
            credit = maxCredit;
        }
        if (credit > 0) {
            LOG.debug("replenishing client [{}] with {} credits", id, credit);
            link.flow(credit);
        }
    }

    @Override
    public void drain(final long timeoutMillis, final Handler<AsyncResult<Void>> drainCompletionHandler) {
        LOG.debug("draining client [{}]", id);
        link.drain(timeoutMillis, drainCompletionHandler);
    }

    @Override
    public void close(final ErrorCondition error) {
        if (error != null) {
            link.setCondition(error);
        }
        link.close();
    }

    @Override
    public String getLinkId() {
        return id;
    }

    @Override
    public String getConnectionId() {
        return Constants.getConnectionId(link);
    }

    @Override
    public String getTargetAddress() {
        return link.getRemoteTarget().getAddress();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        UpstreamReceiverImpl other = (UpstreamReceiverImpl) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }
}