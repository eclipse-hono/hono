package io.vertx.proton;

import io.vertx.core.Handler;
import org.apache.qpid.proton.message.Message;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ProtonSender extends ProtonLink<ProtonSender> {

  void send(byte[] tag, Message message);

  void send(byte[] tag, Message message, Handler<ProtonDelivery> onUpdated);

  boolean sendQueueFull();

  void sendQueueDrainHandler(Handler<ProtonSender> drainHandler);

  /**
   * Sets whether sent deliveries should be automatically locally-settled
   * once they have become remotely-settled by the receiving peer.
   *
   * True by default.
   *
   * @param autoSettle whether deliveries should be auto settled locally after being settled by the receiver
   * @return the sender
   */
  ProtonSender setAutoSettle(boolean autoSettle);

  boolean isAutoSettle();
}
