package com.bazoqa.echogates.event;

import com.bazoqa.echogates.EchoGates;
import com.bazoqa.echogates.util.PortalFrameDetector;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = EchoGates.MODID)
public class PortalActivationHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        InteractionHand hand = event.getHand();
        ItemStack itemStack = player.getItemInHand(hand);

        // Check if player is holding Sculk Heart
        if (!itemStack.is(EchoGates.SCULK_HEART.get())) {
            return;
        }

        // Check if clicked block is reinforced deepslate
        BlockState clickedBlock = level.getBlockState(pos);
        if (!clickedBlock.is(Blocks.REINFORCED_DEEPSLATE)) {
            return;
        }

        // Server-side only
        if (level.isClientSide) {
            return;
        }

        // Try to detect and activate the portal frame
        if (PortalFrameDetector.tryActivatePortal(level, pos, player)) {
            // Success! Consume the sculk heart
            if (!player.isCreative()) {
                itemStack.shrink(1);
            }
            
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§b§oEcho Gate Activated!"), 
                true
            );
            
            // Play activation sounds
            level.playSound(null, pos, 
                net.minecraft.sounds.SoundEvents.END_PORTAL_FRAME_FILL, 
                net.minecraft.sounds.SoundSource.BLOCKS, 
                1.0F, 1.0F);
            
            level.playSound(null, pos, 
                net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 
                net.minecraft.sounds.SoundSource.BLOCKS, 
                1.0F, 1.0F);
            
            event.setCanceled(true);
        }
    }
}