package com.ddd.autospookydoorsmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DETECTION_RADIUS;
    public static final ModConfigSpec.IntValue MAX_DOORS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ENABLED;

    static {
        BUILDER.push("AutoSpookyDoors Settings");

        DETECTION_RADIUS = BUILDER
                .comment("检测半径（方块数）")
                .defineInRange("detectionRadius", 16, 1, 32);

        MAX_DOORS_PER_TICK = BUILDER
                .comment("每次处理周期最多转换的门数量")
                .defineInRange("maxDoorsPerTick", 10, 1, 50);

        ENABLED = BUILDER
                .comment("是否启用模组")
                .define("enabled", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}