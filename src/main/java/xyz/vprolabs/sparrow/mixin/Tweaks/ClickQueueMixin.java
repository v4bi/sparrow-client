package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.config.ConfigRegister;
import xyz.vprolabs.sparrow.mixin.Utils.MinecraftClientAccessor;
import xyz.vprolabs.sparrow.state.ServerSafety;
import xyz.vprolabs.sparrow.state.ClickQueueState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.WindChargeItem;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MinecraftClient.class, PlayerInventory.class})
public class ClickQueueMixin {

    @Unique
    private int sparrow_lastSlot = -1;

    @Unique
    private boolean sparrow_inClickReplay = false;

    @Unique
    private int sparrow_lastInteractionTick = -1;

    @Unique
    private static boolean sparrow_isUtilityItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof EnderPearlItem || item instanceof WindChargeItem;
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), require = 0)
    private void sparrow_captureClick(CallbackInfo ci) {
        if (!ConfigRegister.clickQueue.get() || ServerSafety.isFeatureDisabled("click-relay")) return;

        MinecraftClient client = (MinecraftClient)(Object)this;
        if (client.player == null || client.options == null) return;
        if (client.currentScreen != null) return;
        if (!sparrow_isUtilityItem(client.player.getMainHandStack())) return;

        int cooldown = ((MinecraftClientAccessor) client).getItemUseCooldown();

        // Replay queued click when cooldown expires
        if (ClickQueueState.shouldReplay() && cooldown == 0 && !client.player.isUsingItem()) {
            if (!client.player.getMainHandStack().isEmpty()) {
                if (client.player.age - sparrow_lastInteractionTick >= 2) {
                    ClickQueueState.clear();
                    client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                    sparrow_lastInteractionTick = client.player.age;
                    ((MinecraftClientAccessor) client).setItemUseCooldown(4);
                }
            } else {
                ClickQueueState.clear();
            }
            return;
        }

        boolean keyPressed = client.options.useKey.wasPressed();
        boolean keyHeld = client.options.useKey.isPressed();

        if ((keyPressed || keyHeld) && (cooldown > 0 || client.player.isUsingItem())) {
            ClickQueueState.queue();
        }
    }

    @Inject(method = "setSelectedSlot", at = @At("TAIL"), require = 0)
    private void sparrow_onSlotChange(int slot, CallbackInfo ci) {
        if (sparrow_inClickReplay) return;
        if (slot == sparrow_lastSlot) return;
        sparrow_lastSlot = slot;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Clear stale cooldown from previous item — prevents pearl/windcharge same-tick bug
        if (!client.player.isUsingItem()) {
            ((MinecraftClientAccessor) client).setItemUseCooldown(0);
        }

        if (!ConfigRegister.clickQueue.get() || ServerSafety.isFeatureDisabled("click-relay")) return;
        if (!ClickQueueState.shouldReplay()) return;
        if (client.player.isUsingItem()) return;
        if (client.player.getMainHandStack().isEmpty()) return;
        if (!sparrow_isUtilityItem(client.player.getMainHandStack())) return;

        if (client.player.age - sparrow_lastInteractionTick < 2) return;

        sparrow_inClickReplay = true;
        try {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            sparrow_lastInteractionTick = client.player.age;
        } finally {
            sparrow_inClickReplay = false;
            ClickQueueState.clear();
        }
    }
}
