package com.acme.salary;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Pattern;

final class Money {
    private static final Pattern DECIMAL = Pattern.compile("[0-9]{1,13}(\\.[0-9]{1,2})?");
    private static final long MAX_MINOR = 1_000_000_000_000L;

    private Money() {}

    static long factor(String currency) {
        return BigDecimal.ONE.movePointRight(digits(currency)).longValueExact();
    }

    static long minorUnits(String amount, String currency) {
        if (amount == null || !DECIMAL.matcher(amount).matches()) {
            throw new IllegalArgumentException("Enter an annual salary as a positive number.");
        }
        int digits = digits(currency);
        var decimal = new BigDecimal(amount);
        if (decimal.scale() > digits) {
            throw new IllegalArgumentException(currency + " allows " + digits + " decimal places.");
        }
        long minor = decimal.movePointRight(digits).longValueExact();
        if (minor < 1 || minor > MAX_MINOR) {
            throw new IllegalArgumentException("Salary is outside the supported range.");
        }
        return minor;
    }

    private static int digits(String currency) {
        return Currency.getInstance(currency).getDefaultFractionDigits();
    }
}
