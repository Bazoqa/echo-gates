package com.bazoqa.echogates.data;

import com.bazoqa.echogates.EchoGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stores all active portal positions per dimension and their linked destinations
 */
public class PortalRegistry extends SavedData {
    
    private static final String DATA_NAME = EchoGates.MODID + "_portals";
    private final List<BlockPos> portals = new ArrayList<>();
    private final Map<BlockPos, BlockPos> portalLinks = new HashMap<>(); // portal position -> linked destination
    private final Map<BlockPos, String> portalNames = new HashMap<>();
    
    public PortalRegistry() {
    }
    
    /**
     * Get the portal registry for a dimension
     */
    public static PortalRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(
                PortalRegistry::new,
                PortalRegistry::load
            ),
            DATA_NAME
        );
    }
    
    /**
     * Load portal data from NBT
     */
    public static PortalRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        PortalRegistry registry = new PortalRegistry();
        ListTag portalList = tag.getList("Portals", Tag.TAG_COMPOUND);
        
        for (int i = 0; i < portalList.size(); i++) {
            CompoundTag posTag = portalList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(posTag, "Pos").orElse(null);
            if (pos != null) {
                registry.portals.add(pos);
            }
        }
        
        // Load portal links
        ListTag linksList = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < linksList.size(); i++) {
            CompoundTag linkTag = linksList.getCompound(i);
            BlockPos from = NbtUtils.readBlockPos(linkTag, "From").orElse(null);
            BlockPos to = NbtUtils.readBlockPos(linkTag, "To").orElse(null);
            if (from != null && to != null) {
                registry.portalLinks.put(from, to);
            }
        }

        ListTag namesList = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < namesList.size(); i++) {
            CompoundTag nameTag = namesList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(nameTag, "Pos").orElse(null);
            if (pos != null && nameTag.contains("Name", Tag.TAG_STRING)) {
                registry.portalNames.put(pos, nameTag.getString("Name"));
            }
        }
        
        return registry;
    }
    
    /**
     * Save portal data to NBT
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag portalList = new ListTag();
        
        for (BlockPos pos : portals) {
            CompoundTag posTag = new CompoundTag();
            posTag.put("Pos", NbtUtils.writeBlockPos(pos));
            portalList.add(posTag);
        }
        
        tag.put("Portals", portalList);
        
        // Save portal links
        ListTag linksList = new ListTag();
        for (Map.Entry<BlockPos, BlockPos> entry : portalLinks.entrySet()) {
            CompoundTag linkTag = new CompoundTag();
            linkTag.put("From", NbtUtils.writeBlockPos(entry.getKey()));
            linkTag.put("To", NbtUtils.writeBlockPos(entry.getValue()));
            linksList.add(linkTag);
        }
        
        tag.put("Links", linksList);

        ListTag namesList = new ListTag();
        for (Map.Entry<BlockPos, String> entry : portalNames.entrySet()) {
            CompoundTag nameTag = new CompoundTag();
            nameTag.put("Pos", NbtUtils.writeBlockPos(entry.getKey()));
            nameTag.putString("Name", entry.getValue());
            namesList.add(nameTag);
        }
        tag.put("Names", namesList);
        return tag;
    }
    
    /**
     * Register a new portal
     */
    public void registerPortal(BlockPos pos) {
        if (!portals.contains(pos)) {
            portals.add(pos);
            setDirty();
        }
    }
    
    /**
     * Unregister a portal and clean up all its links
     */
    public void unregisterPortal(BlockPos pos) {
        if (portals.remove(pos)) {
            portalNames.remove(pos);
            // Remove link FROM this portal
            removePortalLink(pos);
            // Remove all links pointing TO this portal
            removeLinksPointingTo(pos);
            setDirty();
        }
    }
    
    /**
     * Get a random portal that's not the current one
     */
    public BlockPos getRandomDestination(BlockPos currentPortal) {
        if (portals.size() <= 1) {
            return null; // No other portals available
        }
        
        List<BlockPos> validDestinations = new ArrayList<>(portals);
        validDestinations.remove(currentPortal);
        
        if (validDestinations.isEmpty()) {
            return null;
        }
        
        Random random = new Random();
        return validDestinations.get(random.nextInt(validDestinations.size()));
    }
    
    /**
     * Get all registered portals
     */
    public List<BlockPos> getAllPortals() {
        return new ArrayList<>(portals);
    }
    
    /**
     * Get the number of registered portals
     */
    public int getPortalCount() {
        return portals.size();
    }

    public void setPortalName(BlockPos portal, String name) {
        portalNames.put(portal, name);
        setDirty();
    }

    public String getPortalName(BlockPos portal) {
        return portalNames.get(portal);
    }
    
    /**
     * Set a portal's linked destination
     */
    public void setPortalLink(BlockPos portal, BlockPos destination) {
        portalLinks.put(portal, destination);
        setDirty();
    }
    
    /**
     * Get a portal's linked destination
     */
    public BlockPos getPortalLink(BlockPos portal) {
        return portalLinks.get(portal);
    }
    
    /**
     * Remove a portal's link
     */
    public void removePortalLink(BlockPos portal) {
        if (portalLinks.remove(portal) != null) {
            setDirty();
        }
    }
    
    /**
     * Remove all links pointing to a specific destination (called when a portal is destroyed)
     */
    public void removeLinksPointingTo(BlockPos destination) {
        portalLinks.entrySet().removeIf(entry -> entry.getValue().equals(destination));
        setDirty();
    }
    
    /**
     * Find the nearest portal to a given position within a radius
     */
    public BlockPos findNearestPortal(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (BlockPos portal : portals) {
            double dist = portal.distSqr(center);
            if (dist <= radius * radius && dist < nearestDist) {
                // Verify the portal actually exists at this location
                if (level.getBlockState(portal).is(EchoGates.ECHO_GATE.get())) {
                    nearest = portal;
                    nearestDist = dist;
                }
            }
        }
        
        return nearest;
    }
}
