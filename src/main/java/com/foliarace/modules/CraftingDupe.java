package com.foliarace.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import static com.foliarace.FoliaRaceAddon.CATEGORY;

/**
 * CRAFTING DUPE - Folia Exploit
 * 
 * Exploits the isRemoved() bug in Folia:
 * In Folia, players are NEVER removed from the world on death - only on disconnect.
 * This means isRemoved() always returns false after death, breaking death checks.
 * 
 * The exploit:
 * 1. Place item in crafting grid (e.g. Melon)
 * 2. Take crafting output (e.g. Melon Seeds) - this consumes the input
 * 3. Send /kill command
 * 4. Disconnect while dead (or immediately after)
 * 5. Reconnect -> Both INPUT and OUTPUT are in inventory = DUPE
 * 
 * Theory: Player data is saved BEFORE item drops are processed due to isRemoved() bug
 */
public class CraftingDupe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Integer> delayAfterDeath = sgGeneral.add(new IntSetting.Builder()
        .name("delay-after-death")
        .description("Ticks to wait after death BEFORE disconnect")
        .defaultValue(5)
        .min(0)
        .max(60)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );
    
    private final Setting<Boolean> fillAllSlots = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-all-slots")
        .description("Fill all 4 crafting slots instead of just 1")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Boolean> disconnectBeforeKill = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-before-kill")
        .description("Option 1: Send disconnect BEFORE /kill")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Integer> delayBeforeKill = sgGeneral.add(new IntSetting.Builder()
        .name("delay-before-kill")
        .description("Option 2: Ticks to wait between craft and kill")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );
    
    private enum State {
        IDLE,
        DEBUG_INVENTORY,
        FILL_GRID,
        WAIT_FILLED,
        TAKE_OUTPUT,        // Take crafting output!
        KILL_DISCONNECT,    // Instant /kill + disconnect
        WAIT_DEATH,
        DEAD_DISCONNECT
    }
    
    private State state = State.IDLE;
    private int stateTicks = 0;
    private int dupeAttempts = 0;
    private boolean wasAlive = true;
    private String originalItemName = "";
    private int originalItemCount = 0;
    
    // Crafting grid slots in PlayerScreenHandler (2x2 grid)
    private static final int[] GRID_SLOTS = {1, 2, 3, 4};
    // Slot 0 = crafting OUTPUT
    
    public CraftingDupe() {
        super(CATEGORY, "crafting-dupe", "Folia isRemoved() Exploit - Crafting + Death Race Condition");
    }
    
    @Override
    public void onActivate() {
        state = State.IDLE;
        stateTicks = 0;
        dupeAttempts = 0;
        wasAlive = true;
        originalItemName = "";
        originalItemCount = 0;
        
        info("§6§l=== CRAFTING DUPE ===");
        info("§e[1] Place item in crafting grid");
        info("§e[2] Take crafting output");
        info("§e[3] /kill + disconnect");
        info("§e[4] Reconnect -> Input + Output = DUPE?");
        info("§f-> Open your inventory (E)!");
    }
    
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        
        stateTicks++;
        
        // Detect death
        boolean isAlive = mc.player.isAlive();
        if (wasAlive && !isAlive && state == State.WAIT_DEATH) {
            info("§c§l=== DIED ===");
            state = State.DEAD_DISCONNECT;
            stateTicks = 0;
        }
        wasAlive = isAlive;
        
        switch (state) {
            case IDLE -> {
                if (mc.currentScreen instanceof InventoryScreen) {
                    info("§a[Start] Inventory open!");
                    state = State.DEBUG_INVENTORY;
                    stateTicks = 0;
                }
            }
            
            case DEBUG_INVENTORY -> {
                PlayerScreenHandler handler = mc.player.playerScreenHandler;
                
                // Count items in inventory (slots 9-44)
                int itemCount = 0;
                int firstItemSlot = -1;
                ItemStack firstItem = ItemStack.EMPTY;
                
                for (int slot = 9; slot < 45; slot++) {
                    ItemStack stack = handler.getSlot(slot).getStack();
                    if (!stack.isEmpty()) {
                        itemCount++;
                        if (firstItemSlot == -1) {
                            firstItemSlot = slot;
                            firstItem = stack;
                        }
                    }
                }
                
                if (firstItemSlot != -1) {
                    info("§a[Inventory] " + itemCount + " slots filled");
                    info("§e[Item] " + firstItem.getName().getString() + " x" + firstItem.getCount());
                    originalItemName = firstItem.getName().getString();
                    originalItemCount = firstItem.getCount();
                    state = State.FILL_GRID;
                    stateTicks = 0;
                } else {
                    warning("§c[ERROR] No items in inventory!");
                    toggle();
                }
            }
            
            case FILL_GRID -> {
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    warning("§c[ERROR] Inventory closed!");
                    state = State.IDLE;
                    return;
                }
                
                PlayerScreenHandler handler = mc.player.playerScreenHandler;
                int slotsToFill = fillAllSlots.get() ? 4 : 1;
                int filled = 0;
                
                // Find items and pick them up
                for (int slot = 9; slot < 45 && filled < slotsToFill; slot++) {
                    ItemStack stack = handler.getSlot(slot).getStack();
                    if (!stack.isEmpty()) {
                        // Pick up item from inventory
                        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                        filled++;
                    }
                }
                
                if (filled > 0) {
                    state = State.WAIT_FILLED;
                    stateTicks = 0;
                } else {
                    warning("§c[ERROR] No item found!");
                    toggle();
                }
            }
            
            case WAIT_FILLED -> {
                if (stateTicks == 2) {
                    // Place item in crafting grid
                    PlayerScreenHandler handler = mc.player.playerScreenHandler;
                    mc.interactionManager.clickSlot(handler.syncId, GRID_SLOTS[0], 0, SlotActionType.PICKUP, mc.player);
                }
                
                if (stateTicks == 5) {
                    PlayerScreenHandler handler = mc.player.playerScreenHandler;
                    ItemStack gridItem = handler.getSlot(GRID_SLOTS[0]).getStack();
                    
                    if (!gridItem.isEmpty()) {
                        info("§a[Grid] " + gridItem.getName().getString() + " x" + gridItem.getCount());
                        state = State.TAKE_OUTPUT;
                        stateTicks = 0;
                    } else {
                        ItemStack cursorStack = handler.getCursorStack();
                        if (!cursorStack.isEmpty()) {
                            info("§e[Cursor] " + cursorStack.getName().getString() + " - placing...");
                            mc.interactionManager.clickSlot(handler.syncId, GRID_SLOTS[0], 0, SlotActionType.PICKUP, mc.player);
                        } else {
                            warning("§c[ERROR] No item in grid or cursor!");
                            toggle();
                        }
                    }
                }
            }
            
            case TAKE_OUTPUT -> {
                // Tick 1: Take crafting output
                if (stateTicks == 1) {
                    PlayerScreenHandler handler = mc.player.playerScreenHandler;
                    ItemStack output = handler.getSlot(0).getStack(); // Slot 0 = crafting output
                    
                    if (!output.isEmpty()) {
                        info("§a[CRAFT] " + output.getName().getString());
                    }
                    
                    // 1. Take the crafting output (this CONSUMES the input item)
                    mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                    
                    // 2. Place crafted item BACK INTO crafting grid
                    mc.interactionManager.clickSlot(handler.syncId, GRID_SLOTS[0], 0, SlotActionType.PICKUP, mc.player);
                    
                    info("§e[Craft done] Waiting " + delayBeforeKill.get() + " ticks...");
                }
                
                // After delay: Kill + Disconnect
                int killTick = 2 + delayBeforeKill.get();
                
                if (stateTicks == killTick) {
                    if (disconnectBeforeKill.get()) {
                        // OPTION 1: Send /kill and disconnect in same tick
                        info("§c§l[DC FIRST] Disconnect!");
                        if (mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendChatCommand("kill");
                            dupeAttempts++;
                            mc.getNetworkHandler().getConnection().disconnect(
                                net.minecraft.text.Text.of("DC Before Kill")
                            );
                        }
                        toggle();
                    } else {
                        // OPTION 2: Kill first, disconnect next tick
                        info("§c§l[KILL] Sent!");
                        mc.getNetworkHandler().sendChatCommand("kill");
                        dupeAttempts++;
                    }
                }
                
                // Tick after kill: Disconnect
                if (stateTicks == killTick + 1 && !disconnectBeforeKill.get()) {
                    info("§c§l[DC] Disconnect!");
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().getConnection().disconnect(
                            net.minecraft.text.Text.of("Instant Dupe")
                        );
                    }
                    toggle();
                }
            }
            
            case KILL_DISCONNECT -> {
                // Not used anymore - everything happens in TAKE_OUTPUT
            }
            
            case WAIT_DEATH -> {
                if (stateTicks > 100) {
                    warning("§c[TIMEOUT] No death after 5 seconds!");
                    toggle();
                }
            }
            
            case DEAD_DISCONNECT -> {
                if (stateTicks >= delayAfterDeath.get()) {
                    info("§c§l[DISCONNECT] Disconnecting WHILE DEAD!");
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().getConnection().disconnect(
                            net.minecraft.text.Text.of("Dead Disconnect Dupe")
                        );
                    }
                    toggle();
                }
            }
        }
    }
    
    @Override
    public void onDeactivate() {
        info("§6[Dupe] Attempts: " + dupeAttempts);
        if (!originalItemName.isEmpty()) {
            info("§e[Check] " + originalItemName + " x" + originalItemCount);
            info("§e[?] Items on ground at death point?");
            info("§e[?] Items in inventory after reconnect?");
        }
    }
    
    @Override
    public String getInfoString() {
        return state.name();
    }
}

