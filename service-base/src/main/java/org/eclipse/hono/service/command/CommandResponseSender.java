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
     * @param correlationId The correlation id of the command.
     * @param contentType The content type describing the response message's payload (may be {@code null}).
     * @param payload The payload or {@code null}.
     * @param properties The properties or {@code null}.
     * @param status The status of the command, which was send to the device.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of tenantId, deviceId, replyId or correlationId is {@code null}.
     */
    Future<ProtonDelivery> sendCommandResponse(
            String correlationId,
            String contentType,
            Buffer payload,
            Map<String, Object> properties,
            int status);

}
