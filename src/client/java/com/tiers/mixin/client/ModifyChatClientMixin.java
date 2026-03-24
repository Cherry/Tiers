package com.tiers.mixin.client;

import com.tiers.TiersClient;
import com.tiers.profile.PlayerProfile;
import com.tiers.profile.Status;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ModifyChatClientMixin {
    @ModifyVariable(at = @At("HEAD"), method = "addMessage", argsOnly = true)
    private Component addMessage(Component original) {
        if (!TiersClient.toggleMod || !TiersClient.toggleChat)
            return original;

        Component text = original;

        for (PlayerProfile playerProfile : TiersClient.playerProfiles) {
            if (playerProfile.status != Status.READY)
                continue;

            if (!text.getString().contains(playerProfile.inGameName))
                continue;

            text = playerProfile.deepReplace(text);
        }

        return text;
    }
}