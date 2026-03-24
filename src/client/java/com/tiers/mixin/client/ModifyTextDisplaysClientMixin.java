package com.tiers.mixin.client;

import com.tiers.TiersClient;
import com.tiers.profile.PlayerProfile;
import com.tiers.profile.Status;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class ModifyTextDisplaysClientMixin {
    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"), method = "splitLines")
    public FormattedText modifyLines(FormattedText original) {
        if (!TiersClient.toggleMod)
            return original;

        int numberOfMatches = 0;
        PlayerProfile detectedPlayerProfile = null;
        for (PlayerProfile playerProfile : TiersClient.playerProfiles) {
            if (original.getString().contains(playerProfile.name) || original.getString().contains(playerProfile.inGameName)) {
                numberOfMatches++;
                detectedPlayerProfile = playerProfile;
            }
        }
        if (numberOfMatches == 1 && detectedPlayerProfile.status == Status.READY && original instanceof Component text)
            return detectedPlayerProfile.deepReplace(text);

        return original;
    }
}