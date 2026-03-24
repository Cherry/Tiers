package com.tiers.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tiers.TiersClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class ModifyNametagsClientMixin {
    @Shadow
    public abstract String getScoreboardName();

    @ModifyReturnValue(at = @At("RETURN"), method = "getDisplayName")
    private Component modifyDisplayName(Component original) {
        return TiersClient.toggleMod ? TiersClient.addGetPlayer(getScoreboardName(), false).getFullName(original) : original;
    }
}