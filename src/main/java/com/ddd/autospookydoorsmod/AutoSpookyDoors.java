package com.ddd.autospookydoorsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(AutoSpookyDoors.MOD_ID)
public class AutoSpookyDoors {
    public static final String MOD_ID = "autospookydoors";

    // 使用映射表方法
    private static final Map<Block, String> DOOR_MAPPING = new HashMap<>();

    static {
        initializeDoorMapping();
    }

    private int tickCounter = 0;
    private int currentPlayerIndex = 0;

    private static void initializeDoorMapping() {
        // 原版门到SpookyDoors门的映射
        DOOR_MAPPING.put(Blocks.OAK_DOOR, "spookydoors:spooky_oak_door");
        DOOR_MAPPING.put(Blocks.SPRUCE_DOOR, "spookydoors:spooky_spruce_door");
        DOOR_MAPPING.put(Blocks.BIRCH_DOOR, "spookydoors:spooky_birch_door");
        DOOR_MAPPING.put(Blocks.JUNGLE_DOOR, "spookydoors:spooky_jungle_door");
        DOOR_MAPPING.put(Blocks.ACACIA_DOOR, "spookydoors:spooky_acacia_door");
        DOOR_MAPPING.put(Blocks.CHERRY_DOOR, "spookydoors:spooky_cherry_door");
        DOOR_MAPPING.put(Blocks.DARK_OAK_DOOR, "spookydoors:spooky_dark_oak_door");
        DOOR_MAPPING.put(Blocks.MANGROVE_DOOR, "spookydoors:spooky_mangrove_door");
        DOOR_MAPPING.put(Blocks.BAMBOO_DOOR, "spookydoors:spooky_bamboo_door");
        DOOR_MAPPING.put(Blocks.CRIMSON_DOOR, "spookydoors:spooky_crimson_door");
        DOOR_MAPPING.put(Blocks.WARPED_DOOR, "spookydoors:spooky_warped_door");
    }

    public AutoSpookyDoors(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!Config.ENABLED.get()) {
            return;
        }

        tickCounter++;

        // 3秒检测间隔
        int tickInterval = 3 * 20;
        if (tickCounter < tickInterval) {
            return;
        }
        tickCounter = 0;

        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }

        if (currentPlayerIndex >= players.size()) {
            currentPlayerIndex = 0;
        }

        ServerPlayer player = players.get(currentPlayerIndex);
        processPlayerDoors(player);

        currentPlayerIndex++;
    }

    private void processPlayerDoors(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int radius = Config.DETECTION_RADIUS.get();

        // 高度限制：玩家Y轴上下6格
        int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - 6);
        int maxY = Math.min(level.getMaxBuildHeight(), playerPos.getY() + 6);

        int centerX = playerPos.getX();
        int centerZ = playerPos.getZ();

        int doorsConverted = 0;
        int maxDoors = Config.MAX_DOORS_PER_TICK.get();

        // 简单的扫描方法
        for (int x = -radius; x <= radius && doorsConverted < maxDoors; x++) {
            for (int z = -radius; z <= radius && doorsConverted < maxDoors; z++) {
                for (int y = minY; y <= maxY && doorsConverted < maxDoors; y++) {
                    BlockPos checkPos = new BlockPos(centerX + x, y, centerZ + z);

                    // 检查距离
                    double distance = Math.sqrt(x*x + z*z + (y-playerPos.getY())*(y-playerPos.getY()));
                    if (distance > radius) {
                        continue;
                    }

                    if (tryConvertDoorAtPosition(level, checkPos)) {
                        doorsConverted++;
                    }
                }
            }
        }
    }

    private boolean tryConvertDoorAtPosition(ServerLevel level, BlockPos pos) {
        // 快速获取方块状态
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // 检查是否是原版门
        if (!DOOR_MAPPING.containsKey(block)) {
            return false;
        }

        // 检查是否是门方块且是下半部分
        if (!(state.getBlock() instanceof DoorBlock)) {
            return false;
        }

        if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
            return false;
        }

        // 获取对应的SpookyDoors门ID
        String spookyDoorId = DOOR_MAPPING.get(block);
        Block spookyDoor = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(spookyDoorId));

        if (spookyDoor == null || spookyDoor == Blocks.AIR) {
            return false;
        }

        // 检查上半部分
        BlockPos upperPos = pos.above();
        if (!level.isLoaded(upperPos)) {
            return false;
        }

        BlockState upperState = level.getBlockState(upperPos);
        if (upperState.getBlock() != block) {
            return false;
        }

        if (!(upperState.getBlock() instanceof DoorBlock)) {
            return false;
        }

        if (upperState.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER) {
            return false;
        }

        // 转换门
        return convertDoorPair(level, pos, upperPos, state, upperState, spookyDoor);
    }

    private boolean convertDoorPair(ServerLevel level, BlockPos lowerPos, BlockPos upperPos,
                                    BlockState lowerState, BlockState upperState, Block spookyDoor) {
        try {
            // 创建新的门状态，复制所有属性
            BlockState newLowerState = spookyDoor.defaultBlockState()
                    .setValue(DoorBlock.FACING, lowerState.getValue(DoorBlock.FACING))
                    .setValue(DoorBlock.OPEN, lowerState.getValue(DoorBlock.OPEN))
                    .setValue(DoorBlock.HINGE, lowerState.getValue(DoorBlock.HINGE))
                    .setValue(DoorBlock.POWERED, lowerState.getValue(DoorBlock.POWERED))
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);

            BlockState newUpperState = spookyDoor.defaultBlockState()
                    .setValue(DoorBlock.FACING, upperState.getValue(DoorBlock.FACING))
                    .setValue(DoorBlock.OPEN, upperState.getValue(DoorBlock.OPEN))
                    .setValue(DoorBlock.HINGE, upperState.getValue(DoorBlock.HINGE))
                    .setValue(DoorBlock.POWERED, upperState.getValue(DoorBlock.POWERED))
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

            // 替换方块
            boolean lowerSuccess = level.setBlock(lowerPos, newLowerState, Block.UPDATE_ALL);
            boolean upperSuccess = level.setBlock(upperPos, newUpperState, Block.UPDATE_ALL);

            return lowerSuccess && upperSuccess;

        } catch (Exception e) {
            return false;
        }
    }
}