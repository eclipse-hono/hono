package io.vertx.proton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.impl.ProtonClientImpl;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ProtonClient {

  static ProtonClient create(Vertx vertx) {
    return new ProtonClientImpl(vertx);
  }

  void connect(String host, int port, Handler<AsyncResult<ProtonConnection>> connectionHandler);

  void connect(String host, int port, String username, String password, Handler<AsyncResult<ProtonConnection>> handler);

  void connect(ProtonClientOptions options, String host, int port, Handler<AsyncResult<ProtonConnection>> connectionHandler);

  void connect(ProtonClientOptions options, String host, int port, String username, String password, Handler<AsyncResult<ProtonConnection>> handler);
}
