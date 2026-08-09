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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({
    ClientPlayerInteractionManager.class,
    ClientPlayNetworkHandler.class,
    InGameHud.class
})
public class HitConfirmMixin {

    // mixinLoaded was called from the InGameHud.render TAIL hook, i.e. EVERY
    // frame: the first call does a log() write and each later call still pays
    // a ConcurrentHashMap add() on the hot path (SparrowLogger's own one-shot
    // registry saves the file I/O, not the per-frame set operation). Guard all
    // three call sites with one flag so the log fires exactly once per session,
    // regardless of which hook runs first (render can precede the first attack
    // event). Static so the flag survives across the copied-in callback methods.
    @Unique
    private static boolean sparrow_hitConfirmLogged = false;

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void sparrow_trackAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
            sparrow_logOnce();
            if (target != null) {
                HudState.registerAttack(target.getId());
            }
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void sparrow_onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
            sparrow_logOnce();
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
        sparrow_logOnce();
        if (MinecraftClient.getInstance().options.hudHidden) return;
            HitConfirmRenderer.render(context, MinecraftClient.getInstance().textRenderer);
    }

    // Private helper: Mixin never injects private methods of the mixin class
    // into the target, so no @Unique needed on the method itself (the field
    // stays @Unique per project rule: mixin fields must be @Unique private).
    private static void sparrow_logOnce() {
        if (!sparrow_hitConfirmLogged) {
            sparrow_hitConfirmLogged = true;
            SparrowLogger.mixinLoaded("HitConfirmMixin");
        }
    }
}
