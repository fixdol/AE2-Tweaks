package com.fxd927.ae2tweaks;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AE2TweaksConfig {
    public static final ModConfigSpec CONFIG;
    public static final ModConfigSpec.IntValue INSCRIBER_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue VIBRATION_CHAMBER_MAX_UPGRADES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        VIBRATION_CHAMBER_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Vibration Chamber")
                .defineInRange("vibration_chamber.max_upgrades", 8, 1, Integer.MAX_VALUE);

        INSCRIBER_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Inscriber")
                .defineInRange("inscriber.max_upgrades", 8, 1, Integer.MAX_VALUE);

        CONFIG = builder.build();
    }
}
