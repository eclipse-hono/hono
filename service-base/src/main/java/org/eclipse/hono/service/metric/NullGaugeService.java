package org.eclipse.hono.service.metric;

import org.springframework.boot.actuate.metrics.GaugeService;

/**
 * A singleton gauge service that does not do anything with submitted values.
 */
public final class NullGaugeService implements GaugeService {

    private NullGaugeService() {}

    /**
     * A no-op gauge metrics service.
     */
    private static class NullGaugeServiceSingleton {
        private static final NullGaugeService INSTANCE = new NullGaugeService();
    }

    /**
     * Gets the singleton instance.
     * 
     * @return The instance.
     */
    public static NullGaugeService getInstance(){
        return NullGaugeService.NullGaugeServiceSingleton.INSTANCE;
    }

    /**
     * This implementation does nothing.
     */
    @Override
    public void submit(final String s, final double v) {
        // do nothing
    }
}
