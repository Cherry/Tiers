package com.tiers.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tiers.TiersClient;
import com.tiers.profile.PlayerProfile;
import com.tiers.profile.Status;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerInfo.class)
public class ModifyTabClientMixin {
    @ModifyReturnValue(at = @At("RETURN"), method = "getTabListDisplayName")
    private Component modifyPlayerName(Component original) {
        if (!TiersClient.toggleMod || !TiersClient.toggleTab || original == null)
            return original;

        String originalString = original.getString();

        for (PlayerProfile playerProfile : TiersClient.playerProfiles)
            if (playerProfile.status == Status.READY && (originalString.contains(playerProfile.name) || originalString.contains(playerProfile.inGameName)))
                return playerProfile.deepReplace(original);

        return original;
    }
}