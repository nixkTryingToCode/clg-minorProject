package com.nixk.recruit.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a company member is allowed to do inside their organisation. Company admins
 * hold every permission; invited recruiters get exactly the ones they were invited with.
 * Users without a company membership (standalone recruiters from before the company
 * workflow) keep full access.
 */
public enum Permission {
    POST_JOB,
    EDIT_JOB,
    EDIT_ANY_JOB,
    VIEW_APPLICANTS,
    UPDATE_STATUS,
    MANAGE_RECRUITERS,
    REASSIGN_JOBS,
    VIEW_ANALYTICS;

    /** Every permission name, in declaration order. */
    public static List<String> allNames() {
        return Arrays.stream(Permission.values()).map(Enum::name).toList();
    }

    /** Parses a stored comma separated permission list; unknown names are ignored. */
    public static Set<Permission> parse(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        Set<Permission> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            String name = s.trim();
            if (name.isEmpty()) continue;
            try {
                out.add(Permission.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // legacy or corrupted value: skip it instead of breaking the member
            }
        }
        return out;
    }

    /** Serialises permissions for storage on a membership or invitation row. */
    public static String join(Iterable<Permission> permissions) {
        StringBuilder sb = new StringBuilder();
        for (Permission p : permissions) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p.name());
        }
        return sb.toString();
    }

    /** Validates a client supplied permission list; throws on anything unknown. */
    public static Set<Permission> validated(List<String> in) {
        Set<Permission> out = new LinkedHashSet<>();
        if (in != null) {
            for (String s : in) {
                if (s == null || s.isBlank()) continue;
                try {
                    out.add(Permission.valueOf(s.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new ApiException(400, "Unknown permission: " + s);
                }
            }
        }
        return out;
    }
}
