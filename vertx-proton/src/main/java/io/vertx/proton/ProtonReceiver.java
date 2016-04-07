package io.vertx.proton;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ProtonReceiver extends ProtonLink<ProtonReceiver> {

  ProtonReceiver flow(int credits);

  ProtonReceiver handler(ProtonMessageHandler handler);

  /**
   * Sets whether received deliveries should be automatically accepted
   * (and settled) after the message handler runs for them, if no other
   * disposition has been applied during handling.
   *
   * True by default.
   *
   * @param autoSettle whether deliveries should be auto accepted after handling if no disposition was applied
   * @return the receiver
   */
  ProtonReceiver setAutoAccept(boolean autoAccept);

  boolean isAutoAccept();

}
