package com.bazoqa.echogates.block;

import com.bazoqa.echogates.EchoGates;
import com.bazoqa.echogates.data.PortalRegistry;
import com.bazoqa.echogates.network.GateNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Collections;
import java.util.List;

public class EchoGateBlock extends Block {
    
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    
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
                                              Player player, net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!stack.is(Items.NAME_TAG)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.has(DataComponents.CUSTOM_NAME)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Rename the name tag in an anvil first."), true);
            }
            return ItemInteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        PortalRegistry registry = PortalRegistry.get(serverLevel);
        BlockPos portalCenter = registry.findNearestPortal(serverLevel, pos, PORTAL_SEARCH_RADIUS);
        if (portalCenter == null) {
            return ItemInteractionResult.FAIL;
        }

        String name = stack.get(DataComponents.CUSTOM_NAME).getString();
        if (name.length() > 64) name = name.substring(0, 64);
        registry.setPortalName(portalCenter, name);
        if (!player.isCreative()) stack.shrink(1);
        player.displayClientMessage(Component.literal("Echo Gate named " + name + "."), true);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return ItemInteractionResult.SUCCESS;
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
        
        PortalRegistry registry = PortalRegistry.get(serverLevel);
        BlockPos currentPortal = registry.findNearestPortal(serverLevel, pos, PORTAL_SEARCH_RADIUS);
        
        if (currentPortal == null) {
            // Not standing in a registered portal
            return;
        }

        if (entity instanceof ServerPlayer player) {
            if (!player.getPersistentData().getBoolean(EchoGates.MODID + ":destination_menu_open")) {
                player.getPersistentData().putBoolean(EchoGates.MODID + ":destination_menu_open", true);
                GateNetwork.openGateScreen(player, currentPortal, registry);
            }
            return;
        }

        List<BlockPos> destinations = registry.getAllPortals();
        destinations.remove(currentPortal);
        Collections.shuffle(destinations);

        BlockPos destination = null;
        for (BlockPos candidate : destinations) {
            serverLevel.getChunkAt(candidate);
            if (serverLevel.getBlockState(candidate).is(EchoGates.ECHO_GATE.get())) {
                destination = candidate;
                break;
            }
            registry.unregisterPortal(candidate);
        }
        if (destination == null) return;
        teleportEntity(entity, destination, false);
        
        // Disable portal usage until entity exits
        entity.getPersistentData().putBoolean(EchoGates.MODID + ":portal_disabled", true);
    }
    
    /**
     * Teleports an entity to the destination portal
     */
    public static void teleportEntity(Entity entity, BlockPos destination, boolean selectedDestination) {
        BlockPos origin = entity.blockPosition();

        // Play the departure sounds before moving the entity.
        entity.level().playSound(null, origin,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
            1.0F, 1.0F);
        entity.level().playSound(null, origin,
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS,
            0.8F, 1.2F);

        // Calculate safe teleport position (center of portal)
        Vec3 targetPos = new Vec3(
            destination.getX() + 0.5,
            destination.getY(),
            destination.getZ() + 0.5
        );
        
        // Teleport the entity
        entity.teleportTo(targetPos.x, targetPos.y, targetPos.z);

        // Play the arrival sounds at the destination.
        entity.level().playSound(null, destination, 
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 
            1.0F, 1.0F);
        
        entity.level().playSound(null, destination, 
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 
            0.8F, 1.2F);
        
        // Display different messages based on teleport type (players only)
        if (entity instanceof ServerPlayer player && !selectedDestination) {
            player.displayClientMessage(
                Component.literal("Teleported to random gate!"),
                true
            );
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
