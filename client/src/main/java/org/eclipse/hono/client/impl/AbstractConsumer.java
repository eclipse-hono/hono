package org.eclipse.hono.client.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
            final int prefetch,
            final BiConsumer<ProtonDelivery, Message> consumer) {

        Future<ProtonReceiver> result = Future.future();
        final String targetAddress = String.format(address, pathSeparator, tenantId);

        context.runOnContext(open -> {
            final ProtonReceiver receiver = con.createReceiver(targetAddress);
            receiver.setAutoAccept(true);
            receiver.setPrefetch(prefetch);
            receiver.openHandler(receiverOpen -> {
                if (receiverOpen.succeeded()) {
                    LOG.debug("{} receiver for [{}] open", address, receiverOpen.result().getRemoteSource());
                    result.complete(receiverOpen.result());
                } else {
                    result.fail(receiverOpen.cause());
                }
            });
            receiver.handler((delivery, message) -> {
                if (consumer != null) {
                    consumer.accept(delivery, message);
                }
            });
            receiver.open();
        });
        return result;
    }

}
