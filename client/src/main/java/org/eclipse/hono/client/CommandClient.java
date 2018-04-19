package org.eclipse.hono.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A client for accessing Hono's Command and Control API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant and device.
 * </p>
 */
public interface CommandClient extends RequestResponseClient {

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected for a successful delivery.
     *
     * @param command The command name.
     * @param data The command data to send to the device.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device. If the response has no payload, the future will complete with {@code null}.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at <a href="https://www.eclipse.org/hono/api/command-and-control-api"/>
     * @throws NullPointerException if command or data is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Buffer> sendCommand(String command, Buffer data);

}
