package org.eclipse.hono.service.command;

import java.net.HttpURLConnection;
import java.util.Map;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Represents a Command and Control API command on the receiver side (adapter). TODO: timeout for receiver/sender
 */
public class Command {

    private Object correlationId;
    private String replyAddress;
    private byte[] messageData;
    private Map<String, Object> applicationProperties;
    private CommandResponder responder;

    Command(CommandResponder responder, Message message) {
        this.responder = responder;
        this.correlationId = message.getMessageId();
        this.replyAddress = message.getReplyTo();
        this.messageData = MessageHelper.getPayload(message).getBytes();
        ApplicationProperties applicationProperties = message.getApplicationProperties();
        if (applicationProperties != null) {
            this.applicationProperties = applicationProperties.getValue();
        }
    }

    void sendResponse(byte[] data, Map<String, Object> properties, Handler<ProtonDelivery> update) {
        Message message = ProtonHelper.message();
        message.setBody(new Data(new Binary(data)));
        message.setCorrelationId(correlationId);
        if (properties != null) {
            message.setApplicationProperties(new ApplicationProperties(properties));
        }
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
        responder.getSender().send(message, update);
    }

    CommandResponder getResponder() {
        return responder;
    }

    /**
     * Gets the reply address of the sender link.
     *
     * @return The reply address.
     */
    public String getReplyAddress() {
        return replyAddress;
    }

    /**
     * Gets the data of the commands request.
     *
     * @return The raw request data.
     */
    public byte[] getRequestData() {
        return messageData;
    }

    /**
     * Gets the AMQP application properties of the request.
     *
     * @return The application properties or {@code null}.
     */
    public Map<String, Object> getRequestProperties() {
        return applicationProperties;
    }

}
