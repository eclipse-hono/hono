/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.impl;

import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonReceiver;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.message.Message;

import static io.vertx.proton.ProtonHelper.accepted;

import java.io.ByteArrayOutputStream;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class ProtonReceiverImpl extends ProtonLinkImpl<ProtonReceiver> implements ProtonReceiver {
    private ProtonMessageHandler handler;

    ProtonReceiverImpl(Receiver receiver) {
        super(receiver);
    }

    @Override
    protected ProtonReceiverImpl self() {
        return this;
    }

    private Receiver getReceiver() {
        return (Receiver) link;
    }

    public int recv(byte[] bytes, int offset, int size) {
        return getReceiver().recv(bytes, offset, size);
    }

    public ProtonReceiver drain(int credit) {
        getReceiver().drain(credit);
        return this;
    }

    @Override
    public ProtonReceiver flow(int credits) {
        //TODO: batch credit replenish, optionally flush if exceeding a given threshold?
        getReceiver().flow(credits);
        flushConnection();
        return this;
    }

    public boolean draining() {
        return getReceiver().draining();
    }

    public ProtonReceiver setDrain(boolean drain) {
        getReceiver().setDrain(drain);
        return this;
    }

    @Override
    public ProtonReceiver handler(ProtonMessageHandler handler) {
        this.handler = handler;
        onDelivery();
        return this;
    }

    private void flushConnection() {
        getSession().getConnectionImpl().flush();
    }

    /////////////////////////////////////////////////////////////////////////////
    //
    // Implementation details hidden from public api.
    //
    /////////////////////////////////////////////////////////////////////////////
    protected ByteArrayOutputStream current = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    private boolean autoAccept = true;

    void onDelivery() {
        if (this.handler == null) {
            return;
        }

        Receiver receiver = getReceiver();
        Delivery delivery = receiver.current();

        if( delivery != null ) {
            int count;
            while ((count = receiver.recv(buffer, 0, buffer.length)) > 0) {
                current.write(buffer, 0, count);
            }

            if (delivery.isPartial()) {
                // Delivery is not yet completely received,
                // return and allow further frames to arrive.
                return;
            }

            byte[] data = current.toByteArray();
            current.reset();

            Message msg = Proton.message();
            msg.decode(data, 0, data.length);

            receiver.advance();

            ProtonDeliveryImpl delImpl = new ProtonDeliveryImpl(delivery);
            handler.handle(delImpl, msg);
            if (autoAccept && delivery.getLocalState() == null) {
                accepted(delImpl, true);
            }
        }
    }

    @Override
    public boolean isAutoAccept() {
        return autoAccept;
    }

    @Override
    public ProtonReceiver setAutoAccept(boolean autoAccept) {
        this.autoAccept = autoAccept;
        return this;
    }
}
