package com.sep.comiverse.entity.enums;

import java.util.Locale;

public enum CreatorPayoutCurrency {
    USD("US Dollar", "$");

    private final String displayName;
    private final String symbol;

    CreatorPayoutCurrency(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public String getCode() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public static CreatorPayoutCurrency fromCode(String value) {
        String normalized = value == null || value.isBlank()
                ? "USD"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!"USD".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported payout currency. Allowed value: USD"
            );
        }
        return USD;
    }
}
