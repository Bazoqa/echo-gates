package com.bazoqa.echogates.block;

import com.bazoqa.echogates.EchoGates;
import com.bazoqa.echogates.data.PortalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public class EchoGateBlock extends Block {
    
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    
    private static final int TELEPORT_TIME = 40; // in ticks
    private static final int PORTAL_SEARCH_RADIUS = 24; // blocks to search for nearest portal
    
    public EchoGateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, 
                                          Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        // Only process on server side
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        
        // Check if player is holding a fermented spider eye - remove portal link
        if (stack.is(Items.FERMENTED_SPIDER_EYE)) {
            ServerLevel serverLevel = (ServerLevel) level;
            PortalRegistry registry = PortalRegistry.get(serverLevel);
            
            BlockPos portalCenter = registry.findNearestPortal(serverLevel, pos, PORTAL_SEARCH_RADIUS);
            if (portalCenter == null) {
                player.displayClientMessage(Component.literal("§c§oNo echo gate found at this location!"), true);
                return ItemInteractionResult.FAIL;
            }
            
            if (registry.getPortalLink(portalCenter) != null) {
                registry.removePortalLink(portalCenter);
                player.displayClientMessage(Component.literal("§e§oGate link removed!"), true);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                return ItemInteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(Component.literal("§c§oThis gate has no link to remove!"), true);
                return ItemInteractionResult.FAIL;
            }
        }
        
        // Check if player is holding an echo shard
        if (!stack.is(Items.ECHO_SHARD)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        
        ServerLevel serverLevel = (ServerLevel) level;
        PortalRegistry registry = PortalRegistry.get(serverLevel);
        
        // Get the portal center position
        BlockPos portalCenter = registry.findNearestPortal(serverLevel, pos, PORTAL_SEARCH_RADIUS);
        if (portalCenter == null) {
            player.displayClientMessage(Component.literal("§c§oNo echo gate found at this location!"), true);
            return ItemInteractionResult.FAIL;
        }
        
        // Check if echo shard has stored coordinates
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        
        if (customData.contains("echogates_gate_x")) {
            // Echo shard has coordinates stored - link this portal to those coordinates
            int targetX = customData.copyTag().getInt("echogates_gate_x");
            int targetY = customData.copyTag().getInt("echogates_gate_y");
            int targetZ = customData.copyTag().getInt("echogates_gate_z");
            BlockPos linkedPos = new BlockPos(targetX, targetY, targetZ);
            
            registry.setPortalLink(portalCenter, linkedPos);
            
            player.displayClientMessage(Component.literal(
                String.format("§b§oGate Linked!§r§7 → [%d, %d, %d]", targetX, targetY, targetZ)), true);
            
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
            
            return ItemInteractionResult.SUCCESS;
        } else {
            // Store this portal's coordinates in the echo shard
            // Only transform one echo shard, even if holding a stack
            ItemStack newStack = new ItemStack(Items.ECHO_SHARD, 1);
            CustomData.update(DataComponents.CUSTOM_DATA, newStack, tag -> {
                tag.putInt("echogates_gate_x", portalCenter.getX());
                tag.putInt("echogates_gate_y", portalCenter.getY());
                tag.putInt("echogates_gate_z", portalCenter.getZ());
            });
            
            // Add enchantment glint
            newStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            
            // Handle stack management
            if (stack.getCount() == 1) {
                // Replace the single item
                player.setItemInHand(hand, newStack);
            } else {
                // Shrink the stack and give the new item to player
                stack.shrink(1);
                if (!player.getInventory().add(newStack)) {
                    // Inventory full, drop the item
                    player.drop(newStack, false);
                }
            }
            
            player.displayClientMessage(Component.literal(
                String.format("§a§oGate Stored!§r§7 [%d, %d, %d]", 
                    portalCenter.getX(), portalCenter.getY(), portalCenter.getZ())), true);
            
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            
            return ItemInteractionResult.SUCCESS;
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // No collision - players can walk through
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Only handle on server side
        if (level.isClientSide) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) level;
        
        // Check if entity is disabled from teleporting (must exit portal first)
        if (entity.getPersistentData().getBoolean(EchoGates.MODID + ":portal_disabled")) {
            // Entity must exit portal before it can teleport again
            return;
        }
        
        // Players have a delay before teleporting, other entities teleport immediately
        boolean isPlayer = entity instanceof Player;
        
        if (isPlayer) {
            long currentTime = level.getGameTime();
            
            // Track how long player has been in portal
            if (!entity.getPersistentData().contains(EchoGates.MODID + ":portal_time")) {
                entity.getPersistentData().putLong(EchoGates.MODID + ":portal_time", currentTime);
                entity.getPersistentData().putLong(EchoGates.MODID + ":in_portal", 1);
                return;
            }
            
            long portalTime = entity.getPersistentData().getLong(EchoGates.MODID + ":portal_time");
            long timeInPortal = currentTime - portalTime;
            
            // Check if player has been in portal long enough
            if (timeInPortal < TELEPORT_TIME) {
                // Spawn sculk soul particles around the player while waiting
                double radius = 1.0;
                for (int i = 0; i < 1; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    double offsetY = Math.random() * 2.0;
                    
                    serverLevel.sendParticles(
                        ParticleTypes.SCULK_SOUL,
                        entity.getX() + offsetX,
                        entity.getY() + offsetY,
                        entity.getZ() + offsetZ,
                        1, // particle count
                        0, 0, 0, // velocity
                        0.0 // speed
                    );
                }
                return;
            }
        }
        
        // Get destination portal
        PortalRegistry registry = PortalRegistry.get(serverLevel);
        BlockPos currentPortal = registry.findNearestPortal(serverLevel, pos, PORTAL_SEARCH_RADIUS);
        
        if (currentPortal == null) {
            // Not standing in a registered portal
            entity.getPersistentData().remove(EchoGates.MODID + ":portal_time");
            entity.getPersistentData().remove(EchoGates.MODID + ":in_portal");
            return;
        }
        
        BlockPos destination = null;
        boolean isLinkedTeleport = false;
        
        // Check if this portal has a linked destination
        BlockPos linkedDestination = registry.getPortalLink(currentPortal);
        if (linkedDestination != null) {
            // Portal has a link - find nearest portal to that location
            destination = registry.findNearestPortal(serverLevel, linkedDestination, PORTAL_SEARCH_RADIUS);
            isLinkedTeleport = true;
            
            if (destination == null) {
                // No portal found at linked destination - show message only for players
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(
                        Component.literal("§c§oLinked Echo Gate not found..."), 
                        true
                    );
                }
                entity.getPersistentData().remove(EchoGates.MODID + ":portal_time");
                entity.getPersistentData().remove(EchoGates.MODID + ":in_portal");
                return;
            }
        } else {
            // No link - use random destination (old behavior)
            destination = registry.getRandomDestination(currentPortal);
            isLinkedTeleport = false;
            
            if (destination == null) {
                // No other portals available - show message only for players
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(
                        Component.literal("§c§oNo other active Echo Gates found..."), 
                        true
                    );
                }
                entity.getPersistentData().remove(EchoGates.MODID + ":portal_time");
                entity.getPersistentData().remove(EchoGates.MODID + ":in_portal");
                return;
            }
        }
        
        // Teleport entity with appropriate message
        teleportEntity(entity, destination, isLinkedTeleport);
        
        // Disable portal usage until entity exits
        entity.getPersistentData().putBoolean(EchoGates.MODID + ":portal_disabled", true);
        entity.getPersistentData().remove(EchoGates.MODID + ":portal_time");
        entity.getPersistentData().remove(EchoGates.MODID + ":in_portal");
    }
    
    /**
     * Teleports an entity to the destination portal
     */
    private void teleportEntity(Entity entity, BlockPos destination, boolean isLinkedTeleport) {
        // Calculate safe teleport position (center of portal)
        Vec3 targetPos = new Vec3(
            destination.getX() + 0.5,
            destination.getY(),
            destination.getZ() + 0.5
        );
        
        // Teleport the entity
        entity.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        
        // Play sounds
        entity.level().playSound(null, entity.blockPosition(), 
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 
            1.0F, 1.0F);

        entity.level().playSound(null, entity.blockPosition(), 
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 
            0.8F, 1.2F);
        
        entity.level().playSound(null, destination, 
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 
            1.0F, 1.0F);
        
        entity.level().playSound(null, destination, 
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 
            0.8F, 1.2F);
        
        // Display different messages based on teleport type (players only)
        if (entity instanceof ServerPlayer player) {
            if (isLinkedTeleport) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§b§oTeleported to linked gate!"), 
                    true
                );
            } else {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§d§oTeleported to random gate!"), 
                    true
                );
            }
        }
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F; // Full brightness, no shadows
    }
}
