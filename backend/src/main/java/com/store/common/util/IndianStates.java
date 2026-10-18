package com.store.common.util;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Canonical state / UT names. The frontend dropdown uses the same list so GST split compares cleanly. */
public final class IndianStates {

    public static final List<String> ALL = List.of(
            "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
            "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka",
            "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal");

    private IndianStates() {}

    public static Optional<String> canonical(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String norm = input.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(s -> s.toLowerCase(Locale.ROOT).equals(norm)).findFirst();
    }

    public static boolean sameState(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
