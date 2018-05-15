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
     * Creates the response message.
     *
     * @param tenantId The tenant that the command response will be send for and the device belongs to.
     * @param deviceId The device that sends the command.
     * @param replyId The reply id as the unique postfix of the replyTo address.
     * @param correlationId The correlation id of the command.
     * @param payload The payload or {@code null}.
     * @param properties The properties or {@code null}.
     * @param status The status of the command, which was send to the device.
     * @return The response message to be send back to the command sender.
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
