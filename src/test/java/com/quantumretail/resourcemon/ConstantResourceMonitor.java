package com.quantumretail.resourcemon;

import java.util.HashMap;
import java.util.Map;

/**
 * Only useful for tests; it returns a constant map.
 *
 */
public class ConstantResourceMonitor implements ResourceMonitor {
    public final Map<String, Double> map;

    public ConstantResourceMonitor(Map<String, Double> map) {
        this.map = map;
    }

    @Override
    public Map<String, Double> getLoad() {
        return map;
    }


    /**
     * expects an even number of parameters in key, value order.
     * @return
     */
    static ConstantResourceMonitor build(Object... params) {
        if (params.length == 0) {
            return new ConstantResourceMonitor(new HashMap<String, Double>());
        }
        if (params.length % 2 == 1) {
            throw new IllegalArgumentException("Expected an even number of parameters (in key, value order)");
        }
        String key = null;
        Map<String, Double> map = new HashMap<String, Double>();
        for (Object param : params) {
            if (key == null) {
                key = (String) param;
            } else {
                map.put(key, (Double) param);
                key = null;
            }
        }
        return new ConstantResourceMonitor(map);
    }
}
