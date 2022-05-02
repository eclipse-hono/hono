/**
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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


import org.eclipse.hono.adapter.ProtocolAdapterProperties;


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
    /**
     * The amount of time (in milliseconds) to wait for a device to acknowledge receiving a command message.
     */
    public static final long DEFAULT_SEND_MESSAGE_TO_DEVICE_TIMEOUT = 1000L; // ms

    private int maxFrameSize = DEFAULT_MAX_FRAME_SIZE_BYTES;
    private int maxSessionFrames = DEFAULT_MAX_SESSION_FRAMES;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT_MILLIS;
    private long sendMessageToDeviceTimeout = DEFAULT_SEND_MESSAGE_TO_DEVICE_TIMEOUT;

    /**
     * Creates properties using default values.
     */
    public AmqpAdapterProperties() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public AmqpAdapterProperties(final AmqpAdapterOptions options) {
        super(options.adapterOptions());
        setIdleTimeout(options.idleTimeout());
        setMaxFrameSize(options.maxFrameSize());
        setMaxSessionFrames(options.maxSessionFrames());
        setSendMessageToDeviceTimeout(options.sendMessageToDeviceTimeout());
    }

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

    /**
     * Gets the time to wait for a delivery update from a device before the AMQP sender link to the
     * device is closed.
     * <p>
     * The default value of this property is {@link #DEFAULT_SEND_MESSAGE_TO_DEVICE_TIMEOUT}.
     *
     * @return The wait time in milliseconds.
     */
    public long getSendMessageToDeviceTimeout() {
        return this.sendMessageToDeviceTimeout;
    }

    /**
     * Sets the time to wait for a delivery update from a device before the AMQP sender link is closed.
     *
     * @param sendMessageToDeviceTimeout The timeout value in milliseconds.
     *
     * @throws IllegalArgumentException if the timeout value is negative.
     */
    public final void setSendMessageToDeviceTimeout(final long sendMessageToDeviceTimeout) {
        if (sendMessageToDeviceTimeout < 0) {
            throw new IllegalArgumentException("timeout value must be >= 0");
        }
        this.sendMessageToDeviceTimeout = sendMessageToDeviceTimeout;
    }
}
