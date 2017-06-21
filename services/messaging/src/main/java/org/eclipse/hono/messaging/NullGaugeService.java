package org.eclipse.hono.messaging;

import org.springframework.boot.actuate.metrics.GaugeService;

/**
 * Empty implementation, to do explicitly nothing
 */
public class NullGaugeService implements GaugeService {

    private NullGaugeService() {}

    private static class NullGaugeServiceSingleton {
        private static final NullGaugeService INSTANCE = new NullGaugeService();
    }

    public static NullGaugeService getInstance(){
        return NullGaugeService.NullGaugeServiceSingleton.INSTANCE;
    }

    @Override
    public void submit(final String s, final double v) {

    }
}
