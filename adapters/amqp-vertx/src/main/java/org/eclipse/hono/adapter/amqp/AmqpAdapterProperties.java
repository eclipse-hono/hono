/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.amqp;

import org.eclipse.hono.config.ProtocolAdapterProperties;


/**
 * Configuration properties for the AMQP protocol adapter.
 *
 */
public class AmqpAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default maximum number of bytes that each AMQP frame may contain.
     */
    public static final int DEFAULT_MAX_FRAME_SIZE_BYTES = 16 * 1024; // 16kb
    /**
     * The default maximum number of frames that can be in flight for an AMQP session.
     */
    public static final int DEFAULT_MAX_SESSION_FRAMES = 30;
    /**
     * The default number of milliseconds to wait for incoming traffic before considering
     * a connection to be stale.
     */
    public static final int DEFAULT_IDLE_TIMEOUT_MILLIS = 60_000;

    private int maxFrameSize = DEFAULT_MAX_FRAME_SIZE_BYTES;
    private int maxSessionFrames = DEFAULT_MAX_SESSION_FRAMES;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT_MILLIS;

    /**
     * Gets the maximum number of bytes that can be sent in an AMQP message delivery
     * over the connection with a device.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_FRAME_SIZE_BYTES}.
     * 
     * @return The frame size.
     */
    public final int getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * Sets the maximum number of bytes that can be sent in an AMQP message delivery
     * over the connection with a device.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_FRAME_SIZE_BYTES}.
     * 
     * @param maxFrameSize The frame size.
     * @throws IllegalArgumentException if the frame size is less than 512 (minimum value
     *           defined by AMQP 1.0 spec).
     */
    public final void setMaxFrameSize(final int maxFrameSize) {
        if (maxFrameSize < 512) {
            throw new IllegalArgumentException("frame size must be at least 512 bytes");
        }
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Gets the maximum number of AMQP transfer frames for sessions created on this connection.
     * This is the number of transfer frames that may simultaneously be in flight for all links
     * in the session.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_SESSION_FRAMES}.
     * 
     * @return The number of frames.
     */
    public final int getMaxSessionFrames() {
        return maxSessionFrames;
    }

    /**
     * Sets the maximum number of AMQP transfer frames for sessions created on this connection.
     * This is the number of transfer frames that may simultaneously be in flight for all links
     * in the session.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_SESSION_FRAMES}.
     * 
     * @param maxSessionFrames The number of frames.
     * @throws IllegalArgumentException if the number is less than 1.
     */
    public final void setMaxSessionFrames(final int maxSessionFrames) {
        if (maxSessionFrames < 1) {
            throw new IllegalArgumentException("must support at least one frame being in flight");
        }
        this.maxSessionFrames = maxSessionFrames;
    }

    /**
     * Gets the maximum AMQP session window size.
     * 
     * @return The product of maxFrameSize and maxSessionFrames.
     */
    public final int getMaxSessionWindowSize() {
        return maxSessionFrames * maxFrameSize;
    }

    /**
     * Sets the time to wait for incoming traffic from a device
     * before the connection should be considered stale and thus be closed.
     * <p>
     * The default value of this property is {@link #DEFAULT_IDLE_TIMEOUT_MILLIS}.
     * 
     * @param timeout The timeout in milliseconds. Setting this value to zero
     *                prevents the adapter from detecting and closing stale connections.
     * @throws IllegalArgumentException if timeout is negative.
     */
    public final void setIdleTimeout(final int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("idle timeout period must be >= 0");
        }
        this.idleTimeout = timeout;
    }

    /**
     * Gets the time to wait for incoming traffic from a device
     * before the connection is considered stale and thus be closed.
     * <p>
     * The default value of this property is {@link #DEFAULT_IDLE_TIMEOUT_MILLIS}.
     * 
     * @return The time interval in milliseconds.
     */
    public final int getIdleTimeout() {
        return this.idleTimeout;
    }
}
