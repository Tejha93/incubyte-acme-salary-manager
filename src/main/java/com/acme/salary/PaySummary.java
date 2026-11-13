package com.acme.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

record PaySummary(String currency, int count, long medianMinor,
                  long averageMinor, long minimumMinor, long maximumMinor) {
    static PaySummary from(String currency, List<Long> ordered) {
        int count = ordered.size();
        var median = BigDecimal.valueOf(ordered.get(count / 2));
        if (count % 2 == 0) {
            median = median.add(BigDecimal.valueOf(ordered.get(count / 2 - 1))).divide(BigDecimal.valueOf(2));
        }
        var total = ordered.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaySummary(currency, count, median.setScale(0, RoundingMode.HALF_UP).longValueExact(),
            total.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact(),
            ordered.get(0), ordered.get(count - 1));
    }
}
