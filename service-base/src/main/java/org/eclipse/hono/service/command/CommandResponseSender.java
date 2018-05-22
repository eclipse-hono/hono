package org.eclipse.hono.service.command;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import org.eclipse.hono.client.MessageSender;

import java.util.Map;

/**
 * A sender to send back the response message of a command.
 */
public interface CommandResponseSender extends MessageSender {

    /**
     * Sends a response message to a command back to the business application.
     *
     * @param tenantId The tenant that the command response will be send for and the device belongs to.
     * @param deviceId The device that sends the command.
     * @param replyId The reply id as the unique postfix of the replyTo address.
     * @param correlationId The correlation id of the command.
     * @param payload The payload or {@code null}.
     * @param properties The properties or {@code null}.
     * @param status The status of the command, which was send to the device.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of tenantId, deviceId, replyId or correlationId is {@code null}.
     */
    Future<ProtonDelivery> sendCommandResponse(
            String tenantId,
            String deviceId,
            String replyId,
            String correlationId,
            Buffer payload,
            Map<String, Object> properties,
            int status);

}
