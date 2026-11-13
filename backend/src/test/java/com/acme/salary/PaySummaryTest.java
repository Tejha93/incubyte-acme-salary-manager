package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaySummaryTest {
    @Test
    void computesMedianAndAverageIndependently() {
        assertThat(PaySummary.from("USD", List.of(10L, 12L, 101L)))
            .isEqualTo(new PaySummary("USD", 3, 12, 41, 10, 101));
    }

    @Test
    void roundsHalfMinorUnitsUp() {
        assertThat(PaySummary.from("JPY", List.of(10L, 11L)))
            .isEqualTo(new PaySummary("JPY", 2, 11, 11, 10, 11));
    }

    @Test
    void handlesSingletonGroups() {
        assertThat(PaySummary.from("GBP", List.of(12L)))
            .isEqualTo(new PaySummary("GBP", 1, 12, 12, 12, 12));
    }
}
