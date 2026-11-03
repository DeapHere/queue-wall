package queuewall.compat;

import java.lang.reflect.Constructor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.world.WorldSettings;

/**
 * The one call that differs between the Minecraft versions Queue Wall supports (1.0 to 1.2.5), so a single jar runs
 * on all of them. Everything else the mod uses has the same intermediary names in every one of these versions.
 *
 * 1.1 added world types: from then on WorldSettings takes a fifth argument, the world type, which Create New World
 * sets to Default unless you pick another. 1.0 has no world types. So this looks up whichever constructor the
 * running version has, and for 1.1 and later passes the Default world type, found through Fabric's mapping
 * resolver by its intermediary name (the same in 1.1 and 1.2.x).
 */
public final class Versions {
    // net.minecraft.world.gen.WorldGeneratorType and its DEFAULT field, in intermediary
    private static final String WORLD_TYPE = "net.minecraft.unmapped.C_1518925";
    private static final String WORLD_TYPE_DEFAULT = "f_8836560";

    private static Constructor<?> settingsConstructor;
    private static Object defaultWorldType;

    private Versions() {
    }

    /** What the Create New World screen makes with an empty seed box: survival, structures on, not hardcore, default world type. */
    public static WorldSettings newWorldSettings(long seed) {
        try {
            if (settingsConstructor == null) findConstructor();
            if (defaultWorldType == null) {
                return (WorldSettings) settingsConstructor.newInstance(seed, 0, true, false);
            }
            return (WorldSettings) settingsConstructor.newInstance(seed, 0, true, false, defaultWorldType);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't create world settings for this Minecraft version", e);
        }
    }

    private static synchronized void findConstructor() throws ReflectiveOperationException {
        if (settingsConstructor != null) return;
        Constructor<?> fourArgs = null;
        Constructor<?> fiveArgs = null;
        for (Constructor<?> c : WorldSettings.class.getConstructors()) {
            if (c.getParameterCount() == 4) fourArgs = c;
            if (c.getParameterCount() == 5) fiveArgs = c;
        }
        if (fourArgs != null) {
            // 1.0
            settingsConstructor = fourArgs;
            return;
        }
        if (fiveArgs == null) throw new NoSuchMethodException("WorldSettings constructor");
        // 1.1 to 1.2.5
        MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
        Class<?> worldType = fiveArgs.getParameterTypes()[4];
        String field = mappings.mapFieldName("intermediary", WORLD_TYPE, WORLD_TYPE_DEFAULT, "L" + WORLD_TYPE.replace('.', '/') + ";");
        defaultWorldType = worldType.getField(field).get(null);
        settingsConstructor = fiveArgs;
    }
}
