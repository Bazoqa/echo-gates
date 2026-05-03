package com.bazoqa.echogates.util;

import com.bazoqa.echogates.EchoGates;
import com.bazoqa.echogates.data.PortalRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class PortalFrameDetector {

    private static final int MAX_PORTAL_WIDTH = 23;
    private static final int MAX_PORTAL_HEIGHT = 23;
    private static final int MIN_PORTAL_SIZE = 2;

    /**
     * Attempts to detect and activate a portal frame starting from the clicked position
     */
    public static boolean tryActivatePortal(Level level, BlockPos clickedPos, Player player) {
        // Try to find a valid frame in all orientations
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            PortalFrame frame = detectFrame(level, clickedPos, axis);
            if (frame != null) {
                if (isValidFrame(level, frame, player)) {
                    // Check if portal is already activated
                    if (isPortalAlreadyActivated(level, frame)) {
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c§oThis Echo Gate is already activated!"),
                            true
                        );
                        return false;
                    }
                    
                    BlockPos portalCenter = fillPortalFrame(level, frame);
                    
                    // Register the portal in the registry
                    if (level instanceof ServerLevel serverLevel) {
                        PortalRegistry.get(serverLevel).registerPortal(portalCenter);
                    }
                    
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects a rectangular portal frame
     */
    private static PortalFrame detectFrame(Level level, BlockPos start, Direction.Axis axis) {
        // Find the bottom-left corner of the frame
        BlockPos corner = findFrameCorner(level, start, axis);
        if (corner == null) {
            return null;
        }

        // Determine frame dimensions
        Direction widthDir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction heightDir = Direction.UP;

        int width = measureFrameSide(level, corner, widthDir, MAX_PORTAL_WIDTH);
        int height = measureFrameSide(level, corner, heightDir, MAX_PORTAL_HEIGHT);

        if (width < MIN_PORTAL_SIZE || height < MIN_PORTAL_SIZE) {
            return null;
        }

        return new PortalFrame(corner, width, height, axis);
    }

    /**
     * Finds the bottom corner of the portal frame
     */
    private static BlockPos findFrameCorner(Level level, BlockPos start, Direction.Axis axis) {
        BlockPos pos = start;
        
        // Move down to find bottom
        while (level.getBlockState(pos.below()).is(Blocks.REINFORCED_DEEPSLATE) && 
               pos.getY() > level.getMinBuildHeight()) {
            pos = pos.below();
        }

        // Move to one side to find edge
        Direction sideDir = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        while (level.getBlockState(pos.relative(sideDir)).is(Blocks.REINFORCED_DEEPSLATE)) {
            pos = pos.relative(sideDir);
        }

        return pos;
    }

    /**
     * Measures how long a frame side extends in a given direction
     */
    private static int measureFrameSide(Level level, BlockPos start, Direction direction, int maxLength) {
        int length = 0;
        BlockPos pos = start;
        
        while (length < maxLength && isReinforcedDeepslateOrVeined(level, pos)) {
            length++;
            pos = pos.relative(direction);
        }
        
        return length;
    }
    
    /**
     * Checks if a position contains reinforced deepslate, ignoring sculk veins
     */
    private static boolean isReinforcedDeepslateOrVeined(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        
        // Direct check for reinforced deepslate
        if (state.is(Blocks.REINFORCED_DEEPSLATE)) {
            return true;
        }
        
        // If it's sculk vein, check adjacent blocks for reinforced deepslate
        // Sculk veins grow ON blocks, so we check around
        if (state.is(Blocks.SCULK_VEIN)) {
            for (Direction dir : Direction.values()) {
                if (level.getBlockState(pos.relative(dir)).is(Blocks.REINFORCED_DEEPSLATE)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Validates that the frame is complete (all sides are reinforced deepslate or sculk veins)
     */
    private static boolean isValidFrame(Level level, PortalFrame frame, Player player) {
        // Check all four sides of the frame
        boolean bottom = checkFrameSide(level, frame, true, true);
        boolean top = checkFrameSide(level, frame, true, false);
        boolean left = checkFrameSide(level, frame, false, true);
        boolean right = checkFrameSide(level, frame, false, false);
        
        return bottom && top && left && right;
    }

    /**
     * Checks if the portal interior already contains portal blocks
     */
    private static boolean isPortalAlreadyActivated(Level level, PortalFrame frame) {
        for (int y = 1; y < frame.height - 1; y++) {
            for (int w = 1; w < frame.width - 1; w++) {
                BlockPos checkPos;
                if (frame.axis == Direction.Axis.X) {
                    checkPos = frame.corner.offset(w, y, 0);
                } else {
                    checkPos = frame.corner.offset(0, y, w);
                }
                
                // If any portal block is found, the portal is already activated
                if (level.getBlockState(checkPos).is(EchoGates.ECHO_GATE.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if one side of the frame is complete
     */
    private static boolean checkFrameSide(Level level, PortalFrame frame, boolean isHorizontal, boolean isFirst) {
        BlockPos start;
        Direction direction;
        int length;

        if (isHorizontal) {
            // Bottom or top edge
            start = isFirst ? frame.corner : frame.corner.above(frame.height - 1);
            direction = frame.axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            length = frame.width;
        } else {
            // Left or right edge
            start = isFirst ? frame.corner : 
                    (frame.axis == Direction.Axis.X ? 
                        frame.corner.east(frame.width - 1) : 
                        frame.corner.south(frame.width - 1));
            direction = Direction.UP;
            length = frame.height;
        }

        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(direction, i);
            if (!isReinforcedDeepslateOrVeined(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fills the interior of the portal frame with portal blocks
     * Returns the center position of the portal
     */
    private static BlockPos fillPortalFrame(Level level, PortalFrame frame) {
        BlockState portalState = EchoGates.ECHO_GATE.get().defaultBlockState()
            .setValue(com.bazoqa.echogates.block.EchoGateBlock.AXIS, frame.axis);
        
        for (int y = 1; y < frame.height - 1; y++) {
            for (int w = 1; w < frame.width - 1; w++) {
                BlockPos fillPos;
                if (frame.axis == Direction.Axis.X) {
                    fillPos = frame.corner.offset(w, y, 0);
                } else {
                    fillPos = frame.corner.offset(0, y, w);
                }
                
                // Replace air blocks and sculk veins
                BlockState currentState = level.getBlockState(fillPos);
                if (currentState.isAir() || currentState.is(Blocks.SCULK_VEIN)) {
                    level.setBlock(fillPos, portalState, 3);
                }
            }
        }
        
        // Calculate and return the center position
        int centerW = frame.width / 2;
        int centerY = frame.height / 2;
        
        if (frame.axis == Direction.Axis.X) {
            return frame.corner.offset(centerW, centerY, 0);
        } else {
            return frame.corner.offset(0, centerY, centerW);
        }
    }

    /**
     * Data class to represent a portal frame
     */
    private static class PortalFrame {
        final BlockPos corner;
        final int width;
        final int height;
        final Direction.Axis axis;

        PortalFrame(BlockPos corner, int width, int height, Direction.Axis axis) {
            this.corner = corner;
            this.width = width;
            this.height = height;
            this.axis = axis;
        }
    }
}
