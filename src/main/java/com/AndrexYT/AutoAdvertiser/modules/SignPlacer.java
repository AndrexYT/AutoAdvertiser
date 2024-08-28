package com.AndrexYT.AutoAdvertiser.addon.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import com.AndrexYT.AutoAdvertiser.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import static meteordevelopment.meteorclient.MeteorClient.mc;

import net.minecraft.text.Text;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;

import javax.sound.sampled.Line;

public class SignPlacer extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgSign = this.settings.createGroup("Sign");
    private final SettingGroup sgSettings = this.settings.createGroup("Settings");
    private final Random random = new Random(); // Initialize Random
    private ScheduledExecutorService scheduler;
    private String[] sign_text = new String[4];
    private Boolean[] highlight;

    // Queue to store the last 5 BlockPos
    private final Queue<BlockPos> signPositions = new LinkedList<>();
    private static final int MAX_SIGNS = 5;

//    @EventHandler
//    private void onSendPacket(PacketEvent.Send event) {
//        if (!(event.packet instanceof UpdateSignC2SPacket)) return;
//        sign_text = ((UpdateSignC2SPacket) event.packet).getText();
//    }

    @Override
    public void onDeactivate() {
        // Clear the sign text
        sign_text = null;
        // Properly shut down the scheduler if it's running
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        super.onDeactivate();
    }

    @Override
    public void onActivate() {
        super.onActivate();

        signPositions.clear();

        // Initialize the scheduler
        scheduler = Executors.newScheduledThreadPool(1);

        // Ensure 'delay' is a valid double
        double delayValue;
        if (delay == null) {
            delayValue = 1.0;
        } else {
            try {
                delayValue = Double.parseDouble(delay.toString());
            } catch (NumberFormatException e) {
                delayValue = 1.0;
            }
        }

        // Ensure delayValue is non-negative and period is positive
        long initialDelayMillis = Math.max(0, (long) (delayValue * 1000)); // Convert to milliseconds
        long periodMillis = Math.max(1, (long) (delayValue * 1000)); // Convert to milliseconds

        // Log the delay value
        mc.player.sendMessage(Text.literal("Delay is: " + delayValue + " seconds"), true);

        // Schedule the task to run at a fixed rate
        scheduler.scheduleAtFixedRate(
            this::sendRandomBlockPosMessage,
            initialDelayMillis,
            periodMillis,
            TimeUnit.MILLISECONDS
        );
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof AbstractSignEditScreen) || sign_text == null) return;

        SignBlockEntity sign = ((AbstractSignEditScreenAccessor) event.screen).getSign();

        mc.player.networkHandler.sendPacket(new UpdateSignC2SPacket(sign.getPos(), true, sign_text[0], sign_text[1], sign_text[2], sign_text[3]));

        event.cancel();
    }

    private final Setting<String> Line1 = sgSign.add(new StringSetting.Builder()
        .name("Line1")
        .description("Line1 of sign.")
        .defaultValue("Line1")
        .build()
    );

    private final Setting<String> Line2 = sgSign.add(new StringSetting.Builder()
        .name("Line2")
        .description("Line2 of sign.")
        .defaultValue("Line2")
        .build()
    );

    private final Setting<String> Line3 = sgSign.add(new StringSetting.Builder()
        .name("Line3")
        .description("Line3 of sign.")
        .defaultValue("Line3")
        .build()
    );

    private final Setting<String> Line4 = sgSign.add(new StringSetting.Builder()
        .name("Line4")
        .description("Line4 of sign.")
        .defaultValue("Line4")
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Delay")
        .description("Delay between sign placing.")
        .defaultValue(3)
        .range(0.01d, 15.0d)
        .build()
    );

    private final Setting<Integer> distance_from_player = sgSettings.add(new IntSetting.Builder()
        .name("Distance")
        .description("How far from player should signs be placed.")
        .defaultValue(3)
        .range(1, 6)
        .build()
    );

    private final Setting<Boolean> random_distance = sgSettings.add(new BoolSetting.Builder()
        .name("Random")
        .description("Should the distance be random (use distance setting as max distance)")
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("Color")
        .description("The color of the marker.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("Scale")
        .description("The size of the marker.")
        .defaultValue(2.0d)
        .range(0.5d, 10.0d)
        .build()
    );

    public SignPlacer() {
        super(AddonTemplate.CATEGORY, "Sign Placer", "Gets text from first sign and then copies it and auto places on other signs.");
    }

    private BlockPos getRandomBlockPos() {
        BlockPos playerPos = mc.player.getBlockPos();
        int initialMaxDistance = distance_from_player.get(); // Store the initial max distance
        BlockPos pos = BlockPos.ORIGIN;

        // Attempt to find a valid position up to 10 times
        for (int attempt = 0; attempt < 10; attempt++) {
            if (random_distance.get()) {
                mc.player.sendMessage(Text.literal("Random is true"), true);

                // Loop to find a valid position
                for (int i = 0; i < 5; i++) {
                    int maxDistance = initialMaxDistance - i; // Decrease max distance with each iteration
                    if (maxDistance < 1) {
                        break; // Stop if maxDistance goes below minDistance
                    }

                    double angle = random.nextDouble() * 2 * Math.PI; // Random angle
                    double radius = 1 + random.nextDouble() * (maxDistance - 1); // Random radius within the allowed range
                    int x = (int) (radius * Math.cos(angle));
                    int z = (int) (radius * Math.sin(angle));
                    pos = playerPos.add(x, 0, z);

                    if (i != 0) {
                        pos = pos.down(i);
                    }

                    if (isValidBlockPos(pos) && CanSignBePlaced(pos)) {
                        break; // If a valid position is found, exit the loop
                    }
                }
            } else {
                mc.player.sendMessage(Text.literal("Random is false"), true);
                // If random_distance is false, generate a position within a circle of max distance
                for (int i = 0; i < 5; i++) {
                    int maxDistance = initialMaxDistance - i; // Decrease max distance with each iteration
                    double angle = random.nextDouble() * 2 * Math.PI; // Random angle
                    double radius = random.nextDouble() * maxDistance; // Random radius within the decreasing max distance
                    int x = (int) (radius * Math.cos(angle));
                    int z = (int) (radius * Math.sin(angle));
                    pos = playerPos.add(x, 0, z);
                    if (i != 0) {
                        pos = pos.down(i);
                    }

                    if (isValidBlockPos(pos) && CanSignBePlaced(pos)) {
                        break; // If a valid position is found, exit the loop
                    }
                }
            }

            // Final check for a valid position
            if (isValidBlockPos(pos) && CanSignBePlaced(pos)) {
                return pos; // Return the valid position
            }

            mc.player.sendMessage(Text.literal("Attempt " + (attempt + 1) + ": Position invalid or blocked, trying again..."), false);
        }

        mc.player.sendMessage(Text.literal("Failed to find a valid position after 10 attempts, aborting."), false);
        return mc.player.getBlockPos(); // Return null if no valid position was found after 10 attempts
    }

    private boolean isValidBlockPos(BlockPos pos) {
        // Check if the position is within the world bounds and is not already occupied
        mc.player.sendMessage(Text.literal("Block pos "+ pos +" is air: " + mc.world.getBlockState(pos).isAir()), false);
        return mc.world.getBlockState(pos).isAir();
    }

    private boolean CanSignBePlaced(BlockPos pos) {
        // Check if the position is within the world bounds, the block is air, and there's no sign already there
        boolean match = isValidBlockPos(pos) && !mc.world.getBlockState(pos.down()).isAir();
        mc.player.sendMessage(Text.literal("Can sign be placed at  "+ pos +" is: " + match + " and also under pos is air: "+!mc.world.getBlockState(pos.down()).isAir()), false);
        return isValidBlockPos(pos) && !mc.world.getBlockState(pos.down()).isAir();
    }

    private void sendRandomBlockPosMessage() {
        BlockPos randomPos = getRandomBlockPos();
        String signText = null;
        sign_text = new String[]{Line1.get(), Line2.get(), Line3.get(), Line4.get()};

        if (sign_text != null) {
            signText = "\n" + sign_text[0] + "\n" + sign_text[1] + "\n" + sign_text[2] + "\n" + sign_text[3];
        }

        // Check for signs in the player's inventory
        moveSignToOffHand();
        assert mc.player != null;
        BlockUtils.place(
            randomPos,
            Hand.OFF_HAND,
            mc.player.getInventory().selectedSlot,
            true,
            50,
            true,
            false,
            false);
        mc.player.sendMessage(Text.literal("Random Block Position: " + randomPos + " with text of " + signText), false);

        addSignPosition(randomPos);
    }

    private void addSignPosition(BlockPos pos) {
        // Add the new position to the queue
        signPositions.add(pos);

        // If we have more than MAX_SIGNS, remove the oldest
        if (signPositions.size() > MAX_SIGNS) {
            signPositions.poll(); // Remove the oldest sign position
        }
    }

    private void moveSignToOffHand() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;

        int offHandSlot = 40; // Off-hand slot index

        // Find the lowest slot with a sign
        int signSlot = -1;
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            Item item = stack.getItem();

            if (item == Items.OAK_SIGN || item == Items.SPRUCE_SIGN || item == Items.BIRCH_SIGN ||
                item == Items.JUNGLE_SIGN || item == Items.ACACIA_SIGN || item == Items.DARK_OAK_SIGN ||
                item == Items.MANGROVE_SIGN || item == Items.CHERRY_SIGN || item == Items.BAMBOO_SIGN ||
                item == Items.CRIMSON_SIGN || item == Items.WARPED_SIGN) {
                signSlot = i;
                break;
            }
        }

        // If no sign is found, notify the player and exit
        if (signSlot == -1) {
            mc.player.sendMessage(Text.literal("No signs found in the inventory."), false);
            return;
        }

        // If the off-hand slot is occupied, move the item to the main inventory
        ItemStack offHandItem = player.getInventory().getStack(offHandSlot);
        if (!offHandItem.isEmpty()) {
            // Find an empty slot manually
            int emptySlot = -1;
            for (int i = 0; i < player.getInventory().main.size(); i++) {
                if (player.getInventory().getStack(i).isEmpty()) {
                    emptySlot = i;
                    break;
                }
            }

            // If no empty slot is found, notify the player and exit
            if (emptySlot == -1) {
                mc.player.sendMessage(Text.literal("No empty slots available to move the item from off-hand."), false);
                return;
            }

            // Move the item from off-hand to empty slot
            InvUtils.move().from(offHandSlot).to(emptySlot);
        }

        // Move the sign to the off-hand
        InvUtils.move().from(signSlot).toOffhand();

        mc.player.sendMessage(Text.literal("Moved sign to off-hand and handled previous off-hand item."), false);
    }

    // Block Highlight
    @EventHandler
    private void onRender3d(Render3DEvent event) {
        // Render only the last MAX_SIGNS BlockPos in the queue
        for (BlockPos pos : signPositions) {
            Box marker = new Box(pos);
            marker.stretch(
                scale.get() * marker.getLengthX(),
                scale.get() * marker.getLengthY(),
                scale.get() * marker.getLengthZ()
            );

            // Render the marker based on the color setting
            event.renderer.box(marker, color.get(), color.get(), ShapeMode.Both, 0);
        }
    }


}
