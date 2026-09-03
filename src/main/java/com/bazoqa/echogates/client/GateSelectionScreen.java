package com.bazoqa.echogates.client;

import com.bazoqa.echogates.network.GateNetwork.TravelToGatePayload;
import com.bazoqa.echogates.network.GateNetwork.GateDestination;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class GateSelectionScreen extends Screen {
    private static final int ROWS_PER_PAGE = 6;
    private final BlockPos source;
    private final String sourceName;
    private final List<GateDestination> destinations;
    private int page;

    public GateSelectionScreen(BlockPos source, String sourceName, List<GateDestination> destinations) {
        super(Component.literal("Choose a Destination"));
        this.source = source;
        this.sourceName = sourceName;
        this.destinations = List.copyOf(destinations);
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int left = width / 2 - 110;
        int top = height / 2 - 75;
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, destinations.size());

        for (int i = start; i < end; i++) {
            GateDestination destination = destinations.get(i);
            String labelText = destination.name() == null || destination.name().isBlank()
                ? format(destination.pos())
                : destination.name() + "  (" + format(destination.pos()) + ")";
            Component label = Component.literal(labelText);
            addRenderableWidget(Button.builder(label, button -> select(destination.pos()))
                .bounds(left, top + (i - start) * 22, 220, 20).build());
        }

        int controlsY = top + ROWS_PER_PAGE * 22 + 5;
        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Previous"), button -> {
                page--;
                rebuildButtons();
            }).bounds(left, controlsY, 75, 20).build());
        }
        if (end < destinations.size()) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), button -> {
                page++;
                rebuildButtons();
            }).bounds(left + 145, controlsY, 75, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(left + 70, controlsY + 24, 80, 20).build());
    }

    private void select(BlockPos destination) {
        PacketDistributor.sendToServer(new TravelToGatePayload(source, destination));
        onClose();
    }

    private static String format(BlockPos pos) {
        return String.format("X: %d   Y: %d   Z: %d", pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 100, 0x55FFFF);
        String currentGate = sourceName == null || sourceName.isBlank()
            ? format(source)
            : sourceName + "  (" + format(source) + ")";
        graphics.drawCenteredString(font, "Current gate: " + currentGate, width / 2, height / 2 - 88, 0xAAAAAA);
        if (destinations.isEmpty()) {
            graphics.drawCenteredString(font, "No other active gates in this dimension.", width / 2, height / 2 - 30, 0xFF7777);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
