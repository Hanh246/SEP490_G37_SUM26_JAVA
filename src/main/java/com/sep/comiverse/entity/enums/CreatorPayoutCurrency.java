package com.sep.comiverse.entity.enums;

import java.util.Locale;
import java.util.Set;

public enum CreatorPayoutCurrency {
    USD("US Dollar", "$"),
    EUR("Euro", "€"),
    CNY("Chinese Yuan (Renminbi)", "¥");

    private static final Set<String> SUPPORTED_CODES = Set.of("USD", "EUR", "CNY");

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
        if (!SUPPORTED_CODES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported payout currency. Allowed values: USD, EUR, CNY"
            );
        }
        return valueOf(normalized);
    }
}
