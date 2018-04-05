package org.eclipse.hono.client;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface CommandClient extends RequestResponseClient {

    /**
     * Sends a command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected for a successful delivery.
     *
     * @param deviceId The id of the device to send a command.
     * @param data The command data to send to the device.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 201 has been received from the device/adapter.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code returned by the service.
     * @throws NullPointerException if device ID is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<byte[]> command(String deviceId, byte[] data);

}
