package org.eclipse.hono.service.command;

import org.eclipse.hono.config.ClientConfigProperties;

/**
 * Properties for configuring the connection from an Adapter to the AMQP Network.
 * The purpose of this class is to set different default values.
 */
public class CommandConfigProperties extends ClientConfigProperties {

    /**
     * The default amount of time to wait for credits after link creation. This
     * is higher as in the client defaults, because for the command response the link
     * is created on demand and the response should not fail.
     */
    public static final long DEFAULT_COMMAND_FLOW_LATENCY = 200L; //ms

    /**
     * The default number of credits issued by the receiver side of a link. For the
     * commands, the flow control is done manually.
     */
    public static final int  DEFAULT_COMMAND_INITIAL_CREDITS = 0;

    /**
     * Sets different defaults for the command response configs.
     */
    public CommandConfigProperties() {
        setFlowLatency(DEFAULT_COMMAND_FLOW_LATENCY);
        setInitialCredits(DEFAULT_COMMAND_INITIAL_CREDITS);
    }
}
