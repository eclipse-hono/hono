package org.eclipse.hono.service.metric;

import org.springframework.boot.actuate.metrics.CounterService;

/**
 * Empty implementation, to do explicitly nothing
 */
public class NullCounterService implements CounterService {

    private NullCounterService() {}

    private static class NullCounterServiceSingleton {
        private static final NullCounterService INSTANCE = new NullCounterService();
    }

    public static NullCounterService getInstance(){
        return NullCounterServiceSingleton.INSTANCE;
    }

    @Override
    public void increment(final String s) {

    }

    @Override
    public void decrement(final String s) {

    }

    @Override
    public void reset(final String s) {

    }


}
