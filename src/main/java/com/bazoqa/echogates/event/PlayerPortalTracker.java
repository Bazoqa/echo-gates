package com.bazoqa.echogates.event;

import com.bazoqa.echogates.EchoGates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = EchoGates.MODID)
public class PlayerPortalTracker {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        BlockState standingIn = player.level().getBlockState(player.blockPosition());
        boolean inPortal = standingIn.is(EchoGates.ECHO_GATE.get());
        
        // If player is no longer standing in an ancient portal, reset everything
        if (!inPortal) {
            // Clear portal timer if it exists
            if (player.getPersistentData().contains(EchoGates.MODID + ":in_portal")) {
                player.getPersistentData().remove(EchoGates.MODID + ":portal_time");
                player.getPersistentData().remove(EchoGates.MODID + ":in_portal");
            }
            
            // Re-enable portal usage
            if (player.getPersistentData().getBoolean(EchoGates.MODID + ":portal_disabled")) {
                player.getPersistentData().putBoolean(EchoGates.MODID + ":portal_disabled", false);
            }
        }
    }
}
