package xyz.vprolabs.sparrow.mixin.Optimization.Bloat;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GoalSelector.class)
public class GoalSelectorBloatMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sparrow_cancelGoalTick(CallbackInfo ci) {
            if (Modules.disableEntityAI.isEnabled()) {
                ci.cancel();
            }
    }
}
