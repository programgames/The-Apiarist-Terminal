package net.ocgendustry.util;

import java.util.List;

/**
 * Small, pure-Java helpers extracted from the Advanced Mutatron driver logic
 * so we can unit test selection behavior without MC/Forge.
 */
public final class MutatronLogic {
    private MutatronLogic() {}

    /**
     * Resolve either a 1-based list index or a raw key present in the keys list.
     * Returns the actual key to use, or null if invalid.
     */
    public static Integer resolveSelectionKey(int n, List<Integer> keysInOrder) {
        if (keysInOrder == null || keysInOrder.isEmpty()) return null;

        // If n is a valid 1-based position, return the key at that position
        if (n >= 1 && n <= keysInOrder.size()) return keysInOrder.get(n - 1);

        // Otherwise treat n as a raw key
        for (Integer k : keysInOrder) {
            if (k != null && k == n) return k;
        }

        return null;
    }
}
