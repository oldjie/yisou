// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure ordering policy: installed-first default, explicit user order takes precedence. */
final class TargetOrdering {
    static List<SearchTarget> resolve(String saved, Set<SearchTarget> installed) {
        List<SearchTarget> defaults = new ArrayList<>();
        for (SearchTarget t : SearchTarget.values()) if (installed.contains(t)) defaults.add(t);
        for (SearchTarget t : SearchTarget.values()) if (!installed.contains(t)) defaults.add(t);
        List<SearchTarget> result = new ArrayList<>();
        if (saved != null) for (String name : saved.split(",")) {
            try { SearchTarget t = SearchTarget.valueOf(name); if (!result.contains(t)) result.add(t); }
            catch (IllegalArgumentException ignored) { }
        }
        if (result.isEmpty()) return defaults;
        for (SearchTarget t : defaults) if (!result.contains(t)) result.add(t);
        return result;
    }
    static String encode(List<SearchTarget> order) {
        StringBuilder saved = new StringBuilder();
        for (SearchTarget t : order) { if (saved.length() > 0) saved.append(','); saved.append(t.name()); }
        return saved.toString();
    }
}
