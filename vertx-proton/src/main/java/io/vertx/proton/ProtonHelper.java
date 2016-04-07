/**
 * Copyright 2015 Red Hat, Inc.
 */

package io.vertx.proton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface ProtonHelper {

    public static Message message() {
        return Proton.message();
    }

    public  static ErrorCondition condition(String name) {
        return new ErrorCondition(Symbol.valueOf(name), null);
    }

    public  static ErrorCondition condition(String name, String description) {
        return new ErrorCondition(Symbol.valueOf(name), description);
    }

    public  static Message message(String body) {
        Message value = message();
        value.setBody(new AmqpValue(body));
        return value;
    }

    public  static Message message(String address, String body) {
        Message value = message(body);
        value.setAddress(address);
        return value;
    }

    public static byte[] tag(String tag) {
        return tag.getBytes(StandardCharsets.UTF_8);
    }


    static <T> AsyncResult<T> future(T value, ErrorCondition err) {
        if (err.getCondition() != null) {
            return Future.failedFuture(err.toString());
        } else {
            return Future.succeededFuture(value);
        }
    }

    public static ProtonDelivery accepted(ProtonDelivery delivery, boolean settle) {
        delivery.disposition(Accepted.getInstance(), settle);
        return delivery;
    }

    public static ProtonDelivery rejected(ProtonDelivery delivery, boolean settle) {
        delivery.disposition(new Rejected(), settle);
        return delivery;
    }

    public static ProtonDelivery released(ProtonDelivery delivery, boolean settle) {
        delivery.disposition(Released.getInstance(), settle);
        return delivery;
    }

    public static ProtonDelivery modified(ProtonDelivery delivery, boolean settle, boolean deliveryFailed, boolean undeliverableHere) {
        Modified modified = new Modified();
        modified.setDeliveryFailed(deliveryFailed);
        modified.setUndeliverableHere(undeliverableHere);

        delivery.disposition(modified, settle);
        return delivery;
    }
}
