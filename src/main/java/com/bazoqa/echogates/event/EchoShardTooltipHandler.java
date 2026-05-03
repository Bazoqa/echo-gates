package com.bazoqa.echogates.event;

import com.bazoqa.echogates.EchoGates;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = EchoGates.MODID, value = Dist.CLIENT)
public class EchoShardTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        
        // Only handle echo shards
        if (!stack.is(Items.ECHO_SHARD)) {
            return;
        }
        
        // Check if it has stored portal coordinates
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        
        if (customData.contains("echogates_gate_x")) {
            int x = customData.copyTag().getInt("echogates_gate_x");
            int y = customData.copyTag().getInt("echogates_gate_y");
            int z = customData.copyTag().getInt("echogates_gate_z");
            
            event.getToolTip().add(Component.literal(""));
            event.getToolTip().add(Component.literal("Destination Gate Location:").withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.literal(String.format(" X: %d", x)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(String.format(" Y: %d", y)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(String.format(" Z: %d", z)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.literal(""));
            event.getToolTip().add(Component.literal("Right-click an Echo Gate to link it to this destination gate").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
