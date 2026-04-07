package com.tiers.mixin.client;

import com.tiers.TiersClient;
import com.tiers.profile.PlayerProfile;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ModifyChatClientMixin {
    @ModifyVariable(at = @At("HEAD"), method = "addMessage", argsOnly = true)
    private Component addMessage(Component contents) {
        if (!TiersClient.toggleMod || !TiersClient.toggleChat)
            return contents;

        return PlayerProfile.getFullyReplaced(contents);
    }
}