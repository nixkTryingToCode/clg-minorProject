package com.nixk.recruit.service;

import java.util.*;
import java.util.stream.Collectors;

/** Tiny helpers for comma-separated list columns. */
public final class Csv {
    private Csv() {}

    public static List<String> split(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        return Arrays.stream(s.split("[,;\\n|]"))
                .map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    public static String join(Collection<String> items) {
        return items == null ? "" : String.join(", ", items);
    }

    public static Set<String> lower(Collection<String> items) {
        Set<String> out = new LinkedHashSet<>();
        for (String i : items) out.add(i.toLowerCase(Locale.ROOT));
        return out;
    }

    public static String nz(String s) {
        return s == null ? "" : s;
    }
}
