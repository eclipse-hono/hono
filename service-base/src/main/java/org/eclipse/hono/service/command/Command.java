package org.eclipse.hono.service.command;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;

import java.util.Map;

/**
 * A command that has been send to the device. Encapsulates the message holds a reference to the CommandAdapter.
 */
public class Command {

    private Message message;
    private CommandAdapter commandAdapter;

    Command(final CommandAdapter commandAdapter, final Message message) {
        this.commandAdapter = commandAdapter;
        this.message = message;
    }

    /**
     * Gets the payload of the command message.
     * @return The payload as Buffer or {@code null}.
     */
    public final Buffer getPayload() {
        return MessageHelper.getPayload(message);
    }

    /**
     * Gets the application properties of the command message.
     * @return The application properties or {@code null}.
     */
    public final Map<String, Object> getApplicationProperties() {
        final ApplicationProperties applicationProperties = message.getApplicationProperties();
        if (applicationProperties != null) {
            return applicationProperties.getValue();
        }
        else {
            return null;
        }
    }

    /**
     * Gets the adapter with receiver/sender to send back the response.
     * 
     * @return The command adapter
     */
    CommandAdapter getCommandAdapter() {
        return commandAdapter;
    }

    /**
     * Gets the replyTo address of the command.
     *
     * @return The replyTo address.
     */
    String getReplyTo() {
        return message.getReplyTo();
    }

    Message createResponseMessage(final Buffer payload, final Map<String, Object> properties, final int status) {
        final Message msg = ProtonHelper.message();
        msg.setCorrelationId(message.getCorrelationId() != null ? message.getCorrelationId() : message.getMessageId());
        msg.setAddress(message.getReplyTo());
        if (payload != null) {
            msg.setBody(new Data(new Binary(payload.getBytes())));
        }
        if (properties != null) {
            msg.setApplicationProperties(new ApplicationProperties(properties));
        }
        MessageHelper.setCreationTime(msg);
        MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_STATUS, status);
        return msg;
    }
}
