package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudState;
import xyz.vprolabs.sparrow.tweaks.HitConfirmRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({
    ClientPlayerInteractionManager.class,
    ClientPlayNetworkHandler.class,
    InGameHud.class
})
public class HitConfirmMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void sparrow_trackAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
            SparrowLogger.mixinLoaded("HitConfirmMixin");
            if (target != null) {
                HudState.registerAttack(target.getId());
            }
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void sparrow_onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
            SparrowLogger.mixinLoaded("HitConfirmMixin");
            byte status = packet.getStatus();
            switch (status) {
                case 2:
                case 33:
                case 34:
                case 35:
                case 54:
                    MinecraftClient client = MinecraftClient.getInstance();
                    // Defer to main thread: onEntityStatus runs on Netty IO thread,
                    // and entity lookup + playSound() access ClientWorld.random.nextLong()
                    // which is LegacyRandomSource (not thread-safe).
                    client.execute(() -> {
                        if (client.world != null) {
                            Entity entity = packet.getEntity(client.world);
                            if (entity != null && entity.getId() == HudState.lastAttackedEntityId) {
                                HudState.confirmHit(entity.getId());
                                if (Modules.hitmarker.isEnabled() && client.player != null) {
                                    client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                                }
                            }
                        }
                    });
                    break;
                default:
                    break;
            }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void sparrow_renderHitConfirm(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        SparrowLogger.mixinLoaded("HitConfirmMixin");
        if (MinecraftClient.getInstance().options.hudHidden) return;
            HitConfirmRenderer.render(context, MinecraftClient.getInstance().textRenderer);
    }
}
