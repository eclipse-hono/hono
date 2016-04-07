/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.impl;

import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonSender;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class ProtonSenderImpl extends ProtonLinkImpl<ProtonSender> implements ProtonSender {

    private Handler<ProtonSender> drainHandler;
    private boolean anonymousSender;
    private boolean autoSettle;

    ProtonSenderImpl(Sender sender) {
        super(sender);
    }

    private Sender sender() {
        return (Sender)link;
    }

    @Override
    public void send(byte[] tag, Message message, Handler<ProtonDelivery> onUpdated) {
        if(anonymousSender && message.getAddress() == null) {
            throw new IllegalArgumentException("Message must have an address when using anonymous sender.");
        }
        // TODO: prevent odd combination of onRecieved callback + SenderSettleMode.SETTLED, or just allow it?

        Delivery delivery = sender().delivery(tag); // start a new delivery..
        int BUFFER_SIZE = 1024;
        byte[] encodedMessage = new byte[BUFFER_SIZE];

        // protonj has a nice encode2 method which tells us what
        // encoded message length would be even if our buffer is too small.

        MessageImpl msg = (MessageImpl) message;
        int len = msg.encode2(encodedMessage, 0, BUFFER_SIZE);

        // looks like the message is bigger than our initial buffer, lets resize and try again.
        if( len > encodedMessage.length ) {
            encodedMessage = new byte[len];
            msg.encode(encodedMessage, 0, len);
        }
        sender().send(encodedMessage, 0, len);

        if( link.getSenderSettleMode() == SenderSettleMode.SETTLED ) {
            delivery.settle();
        }
        sender().advance(); // ends the delivery.

        ProtonDeliveryImpl protonDeliveryImpl = new ProtonDeliveryImpl(delivery);
        if(onUpdated != null) {
            protonDeliveryImpl.setAutoSettle(autoSettle);
            protonDeliveryImpl.handler(onUpdated);
        } else {
            protonDeliveryImpl.setAutoSettle(true);
        }

        getSession().getConnectionImpl().flush();
    }

    @Override
    public void send(byte[] tag, Message message) {
        send(tag, message, null);
    }

    @Override
    public boolean isAutoSettle() {
        return autoSettle;
    }

    @Override
    public ProtonSender setAutoSettle(boolean autoSettle) {
        this.autoSettle = autoSettle;
        return this;
    }

    boolean isAnonymousSender() {
        return anonymousSender;
    }

    void setAnonymousSender(boolean anonymousSender) {
        this.anonymousSender = anonymousSender;
    }

    @Override
    protected ProtonSenderImpl self() {
        return this;
    }

    @Override
    public boolean sendQueueFull() {
        return link.getRemoteCredit() <= 0;
    }


    @Override
    public void sendQueueDrainHandler(Handler<ProtonSender> drainHandler) {
        this.drainHandler = drainHandler;
        fireLinkFlow();
    }

    @Override
    void fireLinkFlow() {
        if( link.getRemoteCredit()>0 && drainHandler!=null ) {
            drainHandler.handle(this);
        }
    }
}
