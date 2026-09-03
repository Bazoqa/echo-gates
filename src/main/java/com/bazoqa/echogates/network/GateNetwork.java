package com.bazoqa.echogates.network;

import com.bazoqa.echogates.EchoGates;
import com.bazoqa.echogates.block.EchoGateBlock;
import com.bazoqa.echogates.client.ClientGatePayloadHandler;
import com.bazoqa.echogates.data.PortalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

public final class GateNetwork {
    private static final int SELECTION_DISTANCE = 32;

    private GateNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(OpenGateScreenPayload.TYPE, OpenGateScreenPayload.STREAM_CODEC,
            ClientGatePayloadHandler::handleOpenScreen);
        registrar.playToServer(TravelToGatePayload.TYPE, TravelToGatePayload.STREAM_CODEC,
            GateNetwork::travelToGate);
    }

    public static void openGateScreen(ServerPlayer player, BlockPos source, PortalRegistry registry) {
        List<GateDestination> destinations = new ArrayList<>();
        for (BlockPos portal : registry.getAllPortals()) {
            if (!portal.equals(source)) {
                destinations.add(new GateDestination(portal, registry.getPortalName(portal)));
            }
        }
        destinations.sort((a, b) -> Double.compare(a.pos().distSqr(source), b.pos().distSqr(source)));
        PacketDistributor.sendToPlayer(player,
            new OpenGateScreenPayload(source, registry.getPortalName(source), destinations));
    }

    private static void travelToGate(TravelToGatePayload payload,
                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        PortalRegistry registry = PortalRegistry.get(level);
        BlockPos source = payload.source();
        if (player.blockPosition().distSqr(source) > SELECTION_DISTANCE * SELECTION_DISTANCE
                || !registry.getAllPortals().contains(source)
                || !level.getBlockState(source).is(EchoGates.ECHO_GATE.get())) {
            player.displayClientMessage(Component.literal("You are too far from that Echo Gate."), true);
            return;
        }

        BlockPos destination = payload.destination();
        if (source.equals(destination) || !registry.getAllPortals().contains(destination)) {
            player.displayClientMessage(Component.literal("That destination gate is no longer available."), true);
            openGateScreen(player, source, registry);
            return;
        }

        // Loading only the selected destination avoids loading every registered
        // gate merely to display the menu.
        level.getChunkAt(destination);
        if (!level.getBlockState(destination).is(EchoGates.ECHO_GATE.get())) {
            registry.unregisterPortal(destination);
            player.displayClientMessage(Component.literal(
                "That destination gate is no longer available. It was removed from the list."), true);
            openGateScreen(player, source, registry);
            return;
        }

        EchoGateBlock.teleportEntity(player, destination, true);
        player.getPersistentData().putBoolean(EchoGates.MODID + ":portal_disabled", true);
        player.getPersistentData().remove(EchoGates.MODID + ":destination_menu_open");
    }

    public record OpenGateScreenPayload(BlockPos source, String sourceName,
                                        List<GateDestination> destinations) implements CustomPacketPayload {
        public static final Type<OpenGateScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EchoGates.MODID, "open_gate_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenGateScreenPayload> STREAM_CODEC =
            StreamCodec.of(OpenGateScreenPayload::write, OpenGateScreenPayload::read);

        private static void write(RegistryFriendlyByteBuf buffer, OpenGateScreenPayload payload) {
            buffer.writeBlockPos(payload.source);
            buffer.writeBoolean(payload.sourceName != null);
            if (payload.sourceName != null) buffer.writeUtf(payload.sourceName, 64);
            buffer.writeVarInt(payload.destinations.size());
            for (GateDestination destination : payload.destinations) {
                buffer.writeBlockPos(destination.pos());
                buffer.writeBoolean(destination.name() != null);
                if (destination.name() != null) buffer.writeUtf(destination.name(), 64);
            }
        }

        private static OpenGateScreenPayload read(RegistryFriendlyByteBuf buffer) {
            BlockPos source = buffer.readBlockPos();
            String sourceName = buffer.readBoolean() ? buffer.readUtf(64) : null;
            int size = buffer.readVarInt();
            List<GateDestination> destinations = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                BlockPos pos = buffer.readBlockPos();
                String name = buffer.readBoolean() ? buffer.readUtf(64) : null;
                destinations.add(new GateDestination(pos, name));
            }
            return new OpenGateScreenPayload(source, sourceName, destinations);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GateDestination(BlockPos pos, String name) {}

    public record TravelToGatePayload(BlockPos source, BlockPos destination) implements CustomPacketPayload {
        public static final Type<TravelToGatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EchoGates.MODID, "travel_to_gate"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TravelToGatePayload> STREAM_CODEC =
            StreamCodec.of(TravelToGatePayload::write, TravelToGatePayload::read);

        private static void write(RegistryFriendlyByteBuf buffer, TravelToGatePayload payload) {
            buffer.writeBlockPos(payload.source);
            buffer.writeBlockPos(payload.destination);
        }

        private static TravelToGatePayload read(RegistryFriendlyByteBuf buffer) {
            return new TravelToGatePayload(buffer.readBlockPos(), buffer.readBlockPos());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
