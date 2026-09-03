package com.bazoqa.echogates.client;

import com.bazoqa.echogates.network.GateNetwork.OpenGateScreenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientGatePayloadHandler {
    private ClientGatePayloadHandler() {}

    public static void handleOpenScreen(OpenGateScreenPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new GateSelectionScreen(
            payload.source(), payload.sourceName(), payload.destinations()));
    }
}
