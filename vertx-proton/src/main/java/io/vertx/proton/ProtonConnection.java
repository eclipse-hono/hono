package io.vertx.proton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
public interface ProtonConnection {

  ProtonConnection setHostname(String hostname);

  ProtonConnection setContainer(String container);

  ProtonConnection setCondition(ErrorCondition condition);

  ErrorCondition getCondition();

  String getContainer();

  String getHostname();

  ErrorCondition getRemoteCondition();

  String getRemoteContainer();

  String getRemoteHostname();

  ProtonConnection open();

  ProtonConnection close();

  /**
   * Creates a receiver used to consumer messages from the given node address.
   *
   * @param address The source address to attach the consumer to.
   *
   * @return the (unopened) consumer.
   */
  ProtonReceiver createReceiver(String address);

  /**
   * Creates a sender used to send messages to the given node address. If no address
   * (i.e null) is specified then a sender will be established to the 'anonymous relay'
   * and each message must specify its destination address.
   *
   * @param address The target address to attach to, or null to attach to the anonymous relay.
   *
   * @return the (unopened) sender.
   */
  ProtonSender createSender(String address);

  /**
   * Allows querying (once the connection has remotely opened) whether the peer
   * advertises support for the anonymous relay (sender with null address).
   *
   * @return true if the peer advertised support for the anonymous relay
   */
  boolean isAnonymousRelaySupported();

  /**
   * Creates a new session, which can be used to create new senders/receivers on.
   *
   * @return the (unopened) session.
   */
  ProtonSession createSession();

  void disconnect();

  boolean isDisconnected();

  ProtonConnection openHandler(Handler<AsyncResult<ProtonConnection>> openHandler);

  ProtonConnection closeHandler(Handler<AsyncResult<ProtonConnection>> closeHandler);

  ProtonConnection disconnectHandler(Handler<ProtonConnection> disconnectHandler);

  ProtonConnection sessionOpenHandler(Handler<ProtonSession> remoteSessionOpenHandler);

  ProtonConnection senderOpenHandler(Handler<ProtonSender> remoteSenderOpenHandler);

  ProtonConnection receiverOpenHandler(Handler<ProtonReceiver> remoteReceiverOpenHandler);

}
