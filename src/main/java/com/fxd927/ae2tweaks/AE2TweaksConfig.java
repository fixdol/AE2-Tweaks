package com.fxd927.ae2tweaks;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AE2TweaksConfig {
    public static final ModConfigSpec CONFIG;
    public static final ModConfigSpec.IntValue COLOR_APPLICATOR_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue FORMATION_PLANE_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue INSCRIBER_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue IO_BUS_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue IO_PORT_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue MATTER_CANNON_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue MOLECULAR_ASSEMBLER_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue PORTABLE_CELL_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue STORAGE_BUS_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue VIBRATION_CHAMBER_MAX_UPGRADES;
    public static final ModConfigSpec.IntValue WIRELESS_TERMINAL_MAX_UPGRADES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COLOR_APPLICATOR_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Color Applicator")
                .defineInRange("color_applicator.max_upgrades", 8, 1, 8);

        FORMATION_PLANE_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Formation Plane")
                .defineInRange("formation_plane.max_upgrades", 8, 1, 8);

        INSCRIBER_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Inscriber")
                .defineInRange("inscriber.max_upgrades", 8, 1, 8);

        IO_BUS_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for IO Bus")
                .defineInRange("io_bus_port.max_upgrades", 8, 1,8);


        IO_PORT_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for IO Port")
                .defineInRange("io_port.max_upgrades", 8, 1, 8);

        MATTER_CANNON_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Matter Cannon")
                .defineInRange("matter_cannon.max_upgrades", 8, 1,8);

        MOLECULAR_ASSEMBLER_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Molecular Assembler")
                .defineInRange("molecular_assembler.max_upgrades", 8, 1,8);

        PORTABLE_CELL_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Portable Cell")
                .defineInRange("portable_cell.max_upgrades", 8, 1,8);

        STORAGE_BUS_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Storage Bus")
                .defineInRange("storage_bus.max_upgrades", 8, 1, 8);

        VIBRATION_CHAMBER_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Vibration Chamber")
                .defineInRange("vibration_chamber.max_upgrades", 8, 1, 8);

        WIRELESS_TERMINAL_MAX_UPGRADES = builder
                .comment("Maximum number of upgrade slots for Wireless Terminal")
                .defineInRange("wireless_terminal_chamber.max_upgrades", 8, 1, 8);

        CONFIG = builder.build();
    }
}
