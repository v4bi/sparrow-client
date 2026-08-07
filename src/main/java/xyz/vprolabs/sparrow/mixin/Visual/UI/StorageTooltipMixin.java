package xyz.vprolabs.sparrow.mixin.visual.ui;

import xyz.vprolabs.sparrow.SparrowMod;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.StoragePreviewState;
import xyz.vprolabs.sparrow.mixin.Utils.HandledScreenAccessor;
import xyz.vprolabs.sparrow.tweaks.StorageTooltipRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(HandledScreen.class)
public class StorageTooltipMixin {

    @Inject(method = "init", at = @At("HEAD"))
    private void sparrow_resetOnInit(CallbackInfo ci) {
            StoragePreviewState.reset();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void sparrow_resetOnClose(CallbackInfo ci) {
            StoragePreviewState.reset();
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void sparrow_replaceTooltipWithGrid(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
            if (!Modules.storageTooltip.isEnabled()) return;

            HandledScreenAccessor acc = (HandledScreenAccessor)(Object)this;
            Slot focusedSlot = acc.getFocusedSlot();
            if (focusedSlot == null) return;

            ItemStack stack = focusedSlot.getStack();
            if (stack.isEmpty()) return;

            if (!StorageTooltipRenderer.hasStorageContent(stack)) return;

            List<ItemStack> items = StorageTooltipRenderer.extractItems(stack);

            ci.cancel();

            StoragePreviewState.lastHoveredSlot = focusedSlot;
            boolean enhanced = SparrowMod.isPreviewKeyPressed();

            if (enhanced) {
                int selectedIndex = StoragePreviewState.getSelectedIndex(focusedSlot, items.size());
                StorageTooltipRenderer.renderWithDetail(context, stack, items, selectedIndex, mouseX, mouseY);
            } else {
                StorageTooltipRenderer.renderBasic(context, stack, items, mouseX, mouseY);
            }
    }
}
