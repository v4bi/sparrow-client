package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
@Environment(EnvType.CLIENT)
public class ViewModelMixin {

    @Unique
    private static boolean sparrow_isWeapon(Item item, ItemStack stack) {
        if (stack.isIn(ItemTags.SWORDS)) return true;
        return item instanceof RangedWeaponItem || item instanceof TridentItem || item instanceof MaceItem
            || item instanceof AxeItem || item instanceof ShovelItem
            || item instanceof HoeItem || item instanceof ShieldItem;
    }

    @Unique
    private static void sparrow_apply(MatrixStack matrices, ItemStack stack) {
        float x = Modules.viewModelX.floatValue();
        float y = Modules.viewModelY.floatValue();
        float z = Modules.viewModelZ.floatValue();
        float size = Modules.viewModelSize.floatValue();

        if (!sparrow_isWeapon(stack.getItem(), stack)) {
            float us = Modules.utilityScale.floatValue();
            if (us > 0.01f && us != 1.0f) {
                matrices.scale(us, us, us);
            }
        }

        if (x != 0.0f || y != 0.0f || z != 0.0f) {
            matrices.translate(x, y, z);
        }
        if (size != 1.0f && size > 0.01f) {
            matrices.scale(size, size, size);
        }
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void onRenderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, MatrixStack matrices, OrderedRenderCommandQueue commandQueue, int light, CallbackInfo ci) {
            if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
                sparrow_apply(matrices, stack);
            }
    }
}
