package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {
    @Test
    void preservesExactMinorUnitsAcrossCurrencies() {
        assertThat(Money.minorUnits("12345.01", "INR")).isEqualTo(1_234_501);
        assertThat(Money.minorUnits("12345", "JPY")).isEqualTo(12_345);
        assertThat(Money.minorUnits("10000000000.00", "USD")).isEqualTo(1_000_000_000_000L);
    }

    @Test
    void rejectsFractionalYen() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.minorUnits("12.01", "JPY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-5", "1.001", "1e2", "12,000", "10000000000.01"})
    void rejectsInvalidAmounts(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.minorUnits(value, "USD"));
    }
}
