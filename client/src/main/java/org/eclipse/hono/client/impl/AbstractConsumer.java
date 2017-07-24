package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Abstract client for consuming messages from a Hono server.
 */
abstract class AbstractConsumer extends AbstractHonoClient implements MessageConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractConsumer.class);

    AbstractConsumer(final Context context, final ProtonReceiver receiver) {
        super(context);
        this.receiver = receiver;
    }

    @Override
    public void flow(final int credits) throws IllegalStateException {
        receiver.flow(credits);
    }

    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeLinks(closeHandler);
    }

    static Future<ProtonReceiver> createConsumer(
            final Context context,
            final ProtonConnection con,
            final String tenantId,
            final String pathSeparator,
            final String address,
            final ProtonQoS qos,
            final int prefetch,
            final BiConsumer<ProtonDelivery, Message> consumer) {

        Future<ProtonReceiver> result = Future.future();
        final String targetAddress = String.format(address, pathSeparator, tenantId);
        final int threshold = prefetch >> 1;

        context.runOnContext(open -> {
            final ProtonReceiver receiver = con.createReceiver(targetAddress);
            receiver.setAutoAccept(true);
            receiver.setPrefetch(0);
            receiver.setQoS(qos);
            receiver.handler((delivery, message) -> {
                if (consumer != null) {
                    consumer.accept(delivery, message);
                }
                int remainingCredits = receiver.getCredit() - receiver.getQueued();
                LOG.trace("handling message [remotely settled: {}, queued messages: {}, remaining credit: {}]", delivery.remotelySettled(), receiver.getQueued(), remainingCredits);
                if (prefetch > 0) {
                    // replenish sender with credits once the remaining credits fall
                    // below threshold (prefetch / 2)
                    if (remainingCredits < threshold) {
                        int credits = prefetch - remainingCredits;
                        LOG.debug("replenishing sender with {} credits ", credits);
                        receiver.flow(credits);
                    }
                }
            });
            receiver.openHandler(receiverOpen -> {
                if (receiverOpen.succeeded()) {
                    LOG.debug("receiver [source: {}, qos: {}] open", receiver.getRemoteSource(), receiver.getRemoteQoS());
                    if (qos.equals(ProtonQoS.AT_LEAST_ONCE) && !qos.equals(receiver.getRemoteQoS())) {
                        LOG.info("remote container uses other QoS than requested [requested: {}, in use: {}]", qos, receiver.getRemoteQoS());
                    }
                    if (prefetch > 0) {
                        receiver.flow(prefetch);
                    }
                    result.complete(receiver);
                } else {
                    result.fail(receiverOpen.cause());
                }
            });
            receiver.open();
        });
        return result;
    }

}
