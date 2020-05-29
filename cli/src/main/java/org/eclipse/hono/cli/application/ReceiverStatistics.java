/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.application;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.AbstractCommand;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A command line client extension to collect a statistic of received messages from via Hono's north bound Telemetry
 * and/or Event API and
 * <p>
 * Statistics are output to stdout.
 */
public class ReceiverStatistics extends AbstractCommand {

    /**
     * The statistics interval in milliseconds.
     */
    private final int interval;

    /**
     * Enable the statistics auto reset.
     *
     * Resets the overall statistics after one quiet interval. May take close to twice the interval since the last
     * received message.
     */
    private boolean autoReset;

    /**
     * Basic message receiver.
     */
    private final Receiver receiver;
    /**
     * Overall statistic.
     */
    private Statistic total;
    /**
     * Current period statistic.
     */
    private Statistic current;
    /**
     * The timer id.
     */
    private long periodicId;
    /**
     * Constructor to create new receiver statistics.
     *
     * @param receiver Receiver instance
     * @param vertx The vert.x instance.
     * @param interval User provided milliseconds of interval.
     * @param autoReset User provided flag to enable the statistics auto reset.
     * @throws NullPointerException if vert.x or receiver is {@code null}.
     */
    public ReceiverStatistics(final Receiver receiver, final Vertx vertx, final int interval, final boolean autoReset) {
        this.receiver = Objects.requireNonNull(receiver);
        this.vertx = Objects.requireNonNull(vertx);
        this.interval = interval;
        this.autoReset = autoReset;
    }

    /**
     * Starts this component.
     * <p>
     *
     * @return A future indicating the outcome of the startup process.
     */
    Future<CompositeFuture> start(final CountDownLatch receiverLatch) {
        this.latch = receiverLatch;
        periodicId = vertx.setPeriodic(interval, this::statistic);
        receiver.setMessageHandler((endpoint, msg) -> handleMessage(endpoint, msg));
        log.info("Statistics [interval: {} ms, autoreset: {}]", interval, autoReset);
        return Future.succeededFuture();
    }

    private void handleMessage(final String endpoint, final Message msg) {
        final String deviceId = MessageHelper.getDeviceId(msg);

        final Buffer payload = MessageHelper.getPayload(msg);

        if (log.isInfoEnabled()) {
            final long now = System.nanoTime();
            final Statistic total;
            Statistic current;
            synchronized (this) {
                if (this.total == null) {
                    this.total = new Statistic(now);
                }
                total = this.total;
                if (this.current == null) {
                    this.current = new PeriodStatistic(now, interval);
                }
                current = this.current;
            }
            total.increment(now);
            if (!current.increment(now)) {
                if (current.isPrinting()) {
                    log.info("statistic: total {}, last {}", total, current);
                }
                synchronized (this) {
                    if (this.current == current) {
                        this.current = new PeriodStatistic(now, interval);
                    }
                    current = this.current;
                }
                current.increment(now);
            }
        }

        log.trace("received {} message [device: {}, content-type: {}]: {}", endpoint, deviceId, msg.getContentType(),
                payload);

        if (msg.getApplicationProperties() != null) {
            log.trace("... with application properties: {}", msg.getApplicationProperties().getValue());
        }
    }

    private void statistic(final Long id) {
        final Statistic total;
        final Statistic current;
        if (latch.getCount() > 0) {
            vertx.cancelTimer(periodicId);
            return;
        }
        synchronized (this) {
            total = this.total;
            current = this.current;
        }
        if (total != null) {
            final long now = System.nanoTime();
            if (current == null || current.finished(now)) {
                if (current == null) {
                    log.info("statistic: total {}", total);
                } else {
                    log.info("statistic: total {}, last {}", total, current);
                }
                synchronized (this) {
                    if (this.total == total && this.current == current) {
                        if (current == null) {
                            if (autoReset) {
                                this.total = null;
                            }
                        } else {
                            this.current = null;
                        }
                    }
                }
            }
        }
    }

    /**
     * Statistic for handled messages.
     */
    private static class Statistic {

        public final AtomicBoolean print = new AtomicBoolean();
        public final AtomicLong counter = new AtomicLong();
        public final long startUptime;
        public volatile long lastUptime;

        /**
         * Create new statistic instance.
         *
         * @param uptime current uptime in nanos
         */
        private Statistic(final long uptime) {
            this.startUptime = uptime;
        }

        /**
         * Signal message received.
         *
         * If the statistic is bound to a period of time, the message may be rejected. This base implementation doesn't
         * offer that function, but a specialized implementation may override this method accordingly.
         *
         * @param uptime current uptime in nanos
         * @return {@code true}, if received message is processed by this statistic, {@code false}, if this statistic
         *         rejected to process the message.
         */
        public boolean increment(final long uptime) {
            lastUptime = uptime;
            counter.incrementAndGet();
            print.set(false);
            return true;
        }

        /**
         * Format statistic.
         */
        public String toString() {
            final long c = counter.get();
            final long period = lastUptime - startUptime;
            if (period > 0) {
                return String.format("%d msgs (%d msgs/s)", c, c * TimeUnit.MILLISECONDS.toNanos(1000) / period);
            } else {
                return String.format("%d msgs", c);
            }
        }

        /**
         * Check, if statistic has finished message processing.
         *
         * @param uptime current uptime in nanos
         * @return {@code false}, if this statistic is not finished yet, {@code true}, if it's finished.
         */
        public boolean finished(final long uptime) {
            return false;
        }

        /**
         * Check, if statistic should be printed.
         *
         * @return {@code true}, if statistic is to be printed, {@code false}, otherwise.
         */
        public boolean isPrinting() {
            return print.compareAndSet(false, true);
        }
    }

    /**
     * Statistic for handled messages of the last period.
     */
    private static class PeriodStatistic extends Statistic {

        public final long uptimeEnd;

        /**
         * Create new time limited statistic instance.
         *
         * @param uptime current uptime in nanos
         */
        private PeriodStatistic(final long uptime, final long interval) {
            super(uptime);
            this.uptimeEnd = uptime + TimeUnit.MILLISECONDS.toNanos(interval);
        }

        @Override
        public boolean increment(final long uptime) {
            if (finished(uptime)) {
                return false;
            } else {
                return super.increment(uptime);
            }
        }

        @Override
        public boolean finished(final long uptime) {
            return uptime > uptimeEnd;
        }
    }
}
