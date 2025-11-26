package com.example.addon.modules.Hypixel.DungeonRoomsHelper.Class;

/**
 * Stark vereinfachte Utils-Version für deinen Meteor-1.21.x-Port.
 *
 * - Kein echtes SkyBlock- oder Dungeon-Erkennen mehr (wir nehmen an, du bist immer im Dungeon).
 * - Keine .skeleton-Roomdateien / kein Filesystem-Kram.
 * - Nur Flags + ein paar Helfer, damit andere Klassen kompilieren.
 */
public class Utils {
    // Wir tun einfach so, als wärst du immer in SkyBlock / Dungeons.
    // Wenn du das später "richtig" machen willst, kannst du das hier umbauen.
    public static boolean inSkyblock = true;
    public static boolean inCatacombs = true;
    public static boolean dungeonOverride = true;

    /**
     * Im Original: checkt per Scoreboard, ob du in SkyBlock bist.
     * Im Meteor-Port: wir setzen es einfach immer auf true.
     */
    public static void checkForSkyblock() {
        inSkyblock = true;
    }

    /**
     * Im Original: checkt per Scoreboard, ob du in den Catacombs bist.
     * Im Meteor-Port: wir setzen es einfach immer auf true.
     */
    public static void checkForCatacombs() {
        inCatacombs = true;
    }

    /**
     * Im Original: Forge-Keybind-Konflikte checken.
     * In Meteor: Keybinds laufen über Settings, wir brauchen das nicht.
     * -> Leere Methode, damit Aufrufe trotzdem kompilieren.
     */
    public static void checkForConflictingHotkeys() {
        // no-op
    }

    /**
     * Im Original: Log-Level nur für DungeonRooms umstellen (Log4j-Config).
     * Für deinen Meteor-Port ist das nicht wichtig.
     * -> Leerer Stub, falls irgendwo noch Utils.setLogLevel(...) aufgerufen wird.
     */
    public static void setLogLevel(Object logger, Object level) {
        // no-op
    }

    /**
     * Packs 4 shorts in ein long.
     * Wird im Original für Room-Daten (ROOM_DATA) verwendet.
     */
    public static long shortToLong(short a, short b, short c, short d) {
        return ((long) ((a << 16) | (b & 0xFFFF)) << 32)
            | (((c << 16) | (d & 0xFFFF)) & 0xFFFFFFFFL);
    }

    /**
     * Entpackt ein long wieder in 4 shorts.
     */
    public static short[] longToShort(long l) {
        return new short[]{
            (short) (l >> 48),
            (short) (l >> 32),
            (short) (l >> 16),
            (short) l
        };
    }
}
