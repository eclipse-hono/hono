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
package org.eclipse.hono.telemetry.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * A telemetry adapter that simply logs and discards all messages.
 * <p>
 * It can be configured to pause a sender periodically for a certain amount of time in order
 * to see if the sender implements flow control correctly.
 */
@Service
@Profile("testing")
public final class MessageDiscardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageDiscardingTelemetryAdapter.class);
    private final long pauseThreshold;
    private final long pausePeriod;
    private Map<String, LinkStatus> statusMap = new HashMap<>();

    public MessageDiscardingTelemetryAdapter() {
        this(0, 0);
    }

    /**
     * @param pauseThreshold the number of messages after which the sender is paused. If set to 0 (zero) the sender will
     *                       never be paused.
     * @param pausePeriod the number of milliseconds after which the sender is resumed.
     */
    public MessageDiscardingTelemetryAdapter(final long pauseThreshold, final long pausePeriod) {
        super(0, 1);
        this.pauseThreshold = pauseThreshold;
        this.pausePeriod = pausePeriod;
    }

    @Override
    public void processTelemetryData(final Message data, final String linkId) {
        LinkStatus status = statusMap.get(linkId);
        if (status == null) {
            LOG.debug("creating new link status object [{}]", linkId);
            status = new LinkStatus(linkId);
            statusMap.put(linkId, status);
        }
        LOG.debug("processing telemetry data [id: {}, to: {}, content-type: {}]", data.getMessageId(), data.getAddress(),
                data.getContentType());
        status.onMsgReceived();
    }

    private class LinkStatus {
        private long msgCount;
        private String linkId;
        private boolean suspended;

        public LinkStatus(final String linkId) {
            this.linkId = linkId;
        }

        public void onMsgReceived() {
            msgCount++;
            if (pauseThreshold > 0 && msgCount % pauseThreshold == 0) {
                pause();
            }
        }

        public void pause() {
            LOG.debug("pausing link [{}]", linkId);
            this.suspended = true;
            sendFlowControlMessage(linkId, true);
            vertx.setTimer(pausePeriod, fired -> {
                vertx.runOnContext(run -> resume());
            });
        }

        private void resume() {
            if (suspended) {
                LOG.debug("resuming link [{}]", linkId);
                sendFlowControlMessage(linkId, false);
                this.suspended = false;
            }
        }
    }
}
