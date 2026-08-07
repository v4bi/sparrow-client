package xyz.vprolabs.sparrow.mixin.Visual;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.PlayerHitState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {
    ClientPlayerInteractionManager.class,
    MinecraftClient.class
})
public class PlayerHitDetectionMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void sparrow_trackAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        SparrowLogger.mixinLoaded("PlayerHitDetectionMixin");
        if (!Modules.playerHitEnabled.isEnabled()) return;
        if (!Modules.playerHitType.stringValue().equals("hit")) return;
        if (target != null) {
            PlayerHitState.registerHit(target.getId());
        }
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void sparrow_perFrameUpdate(boolean tick, CallbackInfo ci) {
        SparrowLogger.mixinLoaded("PlayerHitDetectionMixin");
        if (!Modules.playerHitEnabled.isEnabled()) return;

        PlayerHitState.tick();

        if (!Modules.playerHitType.stringValue().equals("abletohit")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.crosshairTarget == null) {
            PlayerHitState.clearAbleToHit();
            return;
        }
        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            PlayerHitState.clearAbleToHit();
            return;
        }
        Entity target = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (!(target instanceof PlayerEntity)) {
            PlayerHitState.clearAbleToHit();
            return;
        }
        double dist = target.distanceTo(client.player);
        double reach = client.player.getEntityInteractionRange();
        if (dist > reach) {
            PlayerHitState.clearAbleToHit();
            return;
        }
        PlayerHitState.setAbleToHit(target.getId(), dist);
    }
}
