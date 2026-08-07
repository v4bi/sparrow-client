package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(StatusEffect.class)
public class OldPotionColorsMixin {
@Unique
    private static final Map<String, Integer> sparrow_OLD = Map.ofEntries(
        Map.entry("absorption", 0x2552A5),
        Map.entry("bad_omen", 0x0B6138),
        Map.entry("blindness", 0x1F1F23),
        Map.entry("conduit_power", 0x1DC2D1),
        Map.entry("dolphins_grace", 0x88A3BE),
        Map.entry("fire_resistance", 0xE49A3A),
        Map.entry("glowing", 0x94A061),
        Map.entry("haste", 0xD9C043),
        Map.entry("health_boost", 0xF87D23),
        Map.entry("hero_of_the_village", 0x44FF44),
        Map.entry("hunger", 0x587653),
        Map.entry("instant_damage", 0x430A09),
        Map.entry("instant_health", 0xF82423),
        Map.entry("invisibility", 0x7F8392),
        Map.entry("jump_boost", 0x22FF4C),
        Map.entry("levitation", 0xCEFFFF),
        Map.entry("luck", 0x339900),
        Map.entry("mining_fatigue", 0x4A4217),
        Map.entry("nausea", 0x551D4A),
        Map.entry("night_vision", 0x1F1FA1),
        Map.entry("poison", 0x87A363),
        Map.entry("regeneration", 0xCD5CAB),
        Map.entry("resistance", 0x99453A),
        Map.entry("saturation", 0xF82423),
        Map.entry("slow_falling", 0xFFEFD1),
        Map.entry("slowness", 0x5A6C81),
        Map.entry("speed", 0x7CAFC6),
        Map.entry("strength", 0x932423),
        Map.entry("unluck", 0xC0A44D),
        Map.entry("water_breathing", 0x2E5299),
        Map.entry("weakness", 0x484D48),
        Map.entry("wither", 0x352A27)
    );

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void onGetColor(CallbackInfoReturnable<Integer> cir) {
            if (!Modules.oldPotions.isEnabled()) return;
            StatusEffect self = (StatusEffect) (Object) this;
            Identifier id = Registries.STATUS_EFFECT.getId(self);
            if (id != null) {
                Integer oldColor = sparrow_OLD.get(id.getPath());
                if (oldColor != null) {
                    cir.setReturnValue(oldColor);
                }
            }
    }

}
