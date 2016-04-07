package io.vertx.proton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.impl.ProtonServerImpl;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ProtonServer {
  
  static ProtonServer create(Vertx vertx) {
    return new ProtonServerImpl(vertx);
  }

  static ProtonServer create(Vertx vertx, ProtonServerOptions options) {
      return new ProtonServerImpl(vertx, options);
  }

  int actualPort();

  ProtonServer listen(int port);

  ProtonServer listen();

  ProtonServer listen(int port, String host, Handler<AsyncResult<ProtonServer>> handler);

  ProtonServer listen(Handler<AsyncResult<ProtonServer>> handler);

  ProtonServer listen(int port, String host);

  ProtonServer listen(int port, Handler<AsyncResult<ProtonServer>> handler);

  void close();

  void close(Handler<AsyncResult<Void>> handler);

  Handler<ProtonConnection> connectHandler();

  ProtonServer connectHandler(Handler<ProtonConnection> handler);
}
