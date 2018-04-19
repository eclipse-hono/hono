package org.eclipse.hono.util;

/**
 * Commands utility methods used throughout the Command and Control API.
 */
public class CommandConstants {

    /**
     * Empty default constructor.
     */
    protected CommandConstants () {
    }

    /**
     * The name of the Command and Control API. endpoint.
     */
    public static final String COMMAND_ENDPOINT = "control";

    /**
     * The command to be executed by a device.
     */
    public static final String APP_PROPERTY_COMMAND = "command";
}
