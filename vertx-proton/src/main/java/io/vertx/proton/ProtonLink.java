package io.vertx.proton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.amqp.transport.Target;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ProtonLink<T extends ProtonLink> {

    T open();

    T close();

    T openHandler(Handler<AsyncResult<T>> openHandler);

    T closeHandler(Handler<AsyncResult<T>> closeHandler);

    boolean isOpen();

    Target getTarget();

    T setTarget(Target target);

    Target getRemoteTarget();

    Source getSource();

    T setSource(Source source);

    Source getRemoteSource();

    ProtonSession getSession();

    ErrorCondition getCondition();

    T setCondition(ErrorCondition condition);

    ErrorCondition getRemoteCondition();

    ProtonQoS getQoS();
    T setQoS(ProtonQoS qos);
    ProtonQoS getRemoteQoS();
}
