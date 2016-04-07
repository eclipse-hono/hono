/**
 * Copyright 2015 Red Hat, Inc.
 */
package io.vertx.proton.impl;

import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.engine.Delivery;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public class ProtonDeliveryImpl implements ProtonDelivery {

    private final Delivery delivery;
    private Handler<ProtonDelivery> handler;
    private boolean autoSettle;

    ProtonDeliveryImpl(Delivery delivery) {
        this.delivery = delivery;
        delivery.setContext(this);
    }

    public ProtonLinkImpl getLink() {
            return (ProtonLinkImpl) this.delivery.getLink().getContext();
        }

    public void clear() {
        delivery.clear();
    }

    public DeliveryState getLocalState() {
        return delivery.getLocalState();
    }

    public boolean isSettled() {
        return delivery.isSettled();
    }

    @Override
    public boolean remotelySettled() {
        return delivery.remotelySettled();
    }

    public byte[] getTag() {
        return delivery.getTag();
    }

    public void setDefaultDeliveryState(DeliveryState state) {
        delivery.setDefaultDeliveryState(state);
    }

    public DeliveryState getDefaultDeliveryState() {
        return delivery.getDefaultDeliveryState();
    }

    public boolean isReadable() {
        return delivery.isReadable();
    }

    public boolean isUpdated() {
        return delivery.isUpdated();
    }

    public boolean isWritable() {
        return delivery.isWritable();
    }

    public int pending() {
        return delivery.pending();
    }

    public boolean isPartial() {
        return delivery.isPartial();
    }


    public DeliveryState getRemoteState() {
        return delivery.getRemoteState();
    }

    public int getMessageFormat() {
        return delivery.getMessageFormat();
    }

    public boolean isBuffered() {
        return delivery.isBuffered();
    }

    @Override
    public ProtonDelivery disposition(DeliveryState state) {
        disposition(state, false);
        return this;
    }

    @Override
    public ProtonDelivery disposition(DeliveryState state, boolean settle) {
        delivery.disposition(state);
        if(settle) {
            settle();
        } else {
            flushConnection();
        }

        return this;
    }

    @Override
    public ProtonDelivery settle() {
        delivery.settle();
        //TODO: for whatever reason, allowing it to flush
        //here as well actually increases performance...?
        flushConnection();

        //TODO: something nicer to replenish receiver credit?
        if(getLinkImpl() instanceof ProtonReceiverImpl) {
            ((ProtonReceiverImpl) getLinkImpl()).flow(1);
        }
        return this;
    }

    private void flushConnection() {
        getLinkImpl().getSession().getConnectionImpl().flush();
    }

    public ProtonDelivery handler(Handler<ProtonDelivery> handler) {
        this.handler = handler;
        if( delivery.isSettled() ) {
            fireUpdate();
        }
        return this;
    }

    boolean isAutoSettle() {
        return autoSettle;
    }

    void setAutoSettle(boolean autoSettle) {
        this.autoSettle = autoSettle;
    }

    void fireUpdate() {
        if( this.handler!=null ) {
            this.handler.handle(this);
        }

        if(autoSettle && delivery.remotelySettled() && !delivery.isSettled()) {
            settle();
        }
    }

    public ProtonLinkImpl getLinkImpl() {
        return (ProtonLinkImpl) delivery.getLink().getContext();
    }

}
