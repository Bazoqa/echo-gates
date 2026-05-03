package com.bazoqa.echogates.event;

import com.bazoqa.echogates.EchoGates;
import com.bazoqa.echogates.data.PortalRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

@EventBusSubscriber(modid = EchoGates.MODID)
public class PortalBreakHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Level level = event.getPlayer().level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // Only process on server side
        if (level.isClientSide) {
            return;
        }

        // Check if the broken block is an ancient portal block
        if (state.is(EchoGates.ECHO_GATE.get())) {
            // Break all connected portal blocks
            breakConnectedPortal(level, pos);
            return;
        }

        // Check if the broken block is reinforced deepslate (potential frame block)
        if (state.is(Blocks.REINFORCED_DEEPSLATE)) {
            // Check if any adjacent blocks are portal blocks
            for (Direction direction : Direction.values()) {
                BlockPos adjacentPos = pos.relative(direction);
                if (level.getBlockState(adjacentPos).is(EchoGates.ECHO_GATE.get())) {
                    // Found adjacent portal, break the whole portal
                    breakConnectedPortal(level, adjacentPos);
                    return;
                }
            }
        }
    }

    /**
     * Breaks all connected portal blocks using a flood fill algorithm
     */
    private static void breakConnectedPortal(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toCheck = new LinkedList<>();
        
        toCheck.add(startPos);
        visited.add(startPos);

        while (!toCheck.isEmpty()) {
            BlockPos pos = toCheck.poll();
            
            // Remove the portal block
            if (level.getBlockState(pos).is(EchoGates.ECHO_GATE.get())) {
                level.removeBlock(pos, false);
            }

            // Check all adjacent positions
            for (Direction direction : Direction.values()) {
                BlockPos adjacentPos = pos.relative(direction);
                
                if (!visited.contains(adjacentPos) && 
                    level.getBlockState(adjacentPos).is(EchoGates.ECHO_GATE.get())) {
                    visited.add(adjacentPos);
                    toCheck.add(adjacentPos);
                }
            }
        }
        
        // Unregister any portals that were in the broken area
        if (level instanceof ServerLevel serverLevel) {
            PortalRegistry registry = PortalRegistry.get(serverLevel);
            
            // Check all registered portals to see if any are in the broken area
            for (BlockPos portalPos : registry.getAllPortals()) {
                if (visited.contains(portalPos)) {
                    registry.unregisterPortal(portalPos);
                    break; // Only one portal should be in this area
                }
            }
        }
    }
}
