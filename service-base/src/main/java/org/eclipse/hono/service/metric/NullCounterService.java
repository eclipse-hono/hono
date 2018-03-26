package org.eclipse.hono.service.metric;

import org.springframework.boot.actuate.metrics.CounterService;

/**
 * A singleton counter service that does not do anything with submitted values.
 */
public final class NullCounterService implements CounterService {

    private NullCounterService() {}

    /**
     * A no-op metrics counter service.
     */
    private static class NullCounterServiceSingleton {
        private static final NullCounterService INSTANCE = new NullCounterService();
    }

    /**
     * Gets the singleton instance.
     * 
     * @return The instance.
     */
    public static NullCounterService getInstance(){
        return NullCounterServiceSingleton.INSTANCE;
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void increment(final String s) {
        // do nothing
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void decrement(final String s) {
        // do nothing
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void reset(final String s) {
        // do nothing
    }
}
