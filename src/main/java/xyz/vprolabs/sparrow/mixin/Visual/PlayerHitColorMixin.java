package xyz.vprolabs.sparrow.mixin.Visual;

import xyz.vprolabs.sparrow.config.ConfigRegister;
import xyz.vprolabs.sparrow.state.PlayerHitState;
import xyz.vprolabs.sparrow.util.ColorUtil;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class PlayerHitColorMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void sparrow_overrideHurt(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!ConfigRegister.playerHit.get()) return;
        if (!(entity instanceof PlayerEntity player)) return;
        int id = player.getId();
        boolean highlight;
        if (ConfigRegister.playerHitType.get().equals("hit")) {
            highlight = PlayerHitState.isRecentlyHit(id);
        } else {
            highlight = PlayerHitState.ableToHitEntityId == id;
        }
        if (highlight) {
            state.hurt = true;
        }
    }

    @Inject(method = "getMixColor", at = @At("RETURN"), cancellable = true)
    private void sparrow_hitMixColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (!ConfigRegister.playerHit.get()) return;
        if (!(state instanceof PlayerEntityRenderState)) return;
        if (!state.hurt) return;
        int color = ColorUtil.parseArgb(ConfigRegister.playerHitColor.get(), 0x80, 0x80FF0000);
        cir.setReturnValue(color);
    }
}
