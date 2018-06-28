package org.eclipse.hono.adapter.amqp;

/**
 * Represent constants used throughout the Amqp adapter code base.
 */
public final class AmqpAdapterConstants {

    /**
     * They key that an authenticated client of a protocol adapter (representing a device)
     * is stored under in a {@code ProtonConnection}'s attachments.
     */
    public static final String KEY_CLIENT_DEVICE = "CLIENT_DEVICE";

    private AmqpAdapterConstants() {
        // avoid instantiation
    }
}
