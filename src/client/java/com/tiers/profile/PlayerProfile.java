package com.tiers.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tiers.TiersClient;
import com.tiers.misc.Mode;
import com.tiers.profile.types.MCTiersProfile;
import com.tiers.profile.types.PvPTiersProfile;
import com.tiers.profile.types.SubtiersProfile;
import com.tiers.profile.types.SuperProfile;
import com.tiers.textures.ColorControl;
import com.tiers.textures.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.tiers.TiersClient.*;

public class PlayerProfile {
    public Status status;
    public int imageSaved;
    public int numberOfImageRequests;
    public String inGameName;
    public boolean nameChanged;

    public String name = "";
    public String uuid = "";

    public MCTiersProfile profileMCTiers;
    public PvPTiersProfile profilePvPTiers;
    public SubtiersProfile profileSubtiers;

    public Component toAppendLeft;
    public Component toAppendRight;
    private Component fullName;
    private Component deepReplaceName;

    private int numberOfRequests;
    private final boolean regular;

    private static final String UUID_API_1 = "https://playerdb.co/api/player/minecraft/";
    private static final String UUID_API_2 = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String UUID_API_3 = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static boolean forceNewRequest;

    public PlayerProfile(String name, boolean regular) {
        if (name.contains("-force")) {
            String[] content = name.split("-");
            if (content.length == 2) {
                name = content[0];
                forceNewRequest = true;
            }
        }

        this.regular = regular;
        this.name = name;
        inGameName = name;

        status = !name.matches("^[a-zA-Z0-9_]{3,16}$") ? Status.NOT_PLAYER : Status.SEARCHING;
    }

    public PlayerProfile(String mojangJson, String jsonMCTiers, String jsonPvPTiers, String jsonSubtiers) {
        regular = false;

        if (JsonParser.parseString(mojangJson).isJsonNull()) {
            status = Status.API_ISSUE;
            return;
        }

        JsonObject jsonObject = JsonParser.parseString(mojangJson).getAsJsonObject();

        if (jsonObject.has("name") && jsonObject.has("id")) {
            name = jsonObject.get("name").getAsString();
            uuid = jsonObject.get("id").getAsString();
        } else {
            status = Status.NOT_EXISTING;
            return;
        }

        Path path = FabricLoader.getInstance().getGameDir().resolve("cache/tiers/06ec3577329945fabbdf613b1f86c8ab.png");

        try (InputStream inputStream = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("minecraft", "textures/default.png")).orElseThrow().open()) {
            Files.createDirectories(path.getParent());
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            LOGGER.warn("Error copying default skin");
        }

        profileMCTiers = new MCTiersProfile(jsonMCTiers);
        profilePvPTiers = new PvPTiersProfile(jsonPvPTiers);
        profileSubtiers = new SubtiersProfile(jsonSubtiers);

        updateAppendingText();

        status = Status.READY;
    }

    public void buildRequest() {
        if (status != Status.SEARCHING)
            return;

        if (numberOfRequests == 3) {
            buildRequest(UUID_API_2);
            return;
        }

        numberOfRequests++;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(UUID_API_1 + name))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                int statusCode = response.statusCode();

                if (statusCode == 400 || statusCode == 500) {
                    status = Status.NOT_EXISTING;
                    return;
                } else if (statusCode != 200) {
                    buildRequest(UUID_API_2);
                    return;
                }
                parseJson(response.body());
            }).exceptionally(ignored -> {
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(this::buildRequest);
                return null;
            });
        }
    }

    public void buildRequest(String apiUrl) {
        if (numberOfRequests == 12 || status != Status.SEARCHING) {
            status = Status.TIMEOUTED;
            return;
        }

        numberOfRequests++;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + name))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                if (response.body().contains("minecraft/profile/lookup")) {
                    status = Status.API_ISSUE;
                    return;
                }

                int statusCode = response.statusCode();

                if (statusCode == 404 || statusCode == 400) {
                    status = Status.NOT_EXISTING;
                    return;
                } else if (statusCode == 403) {
                    CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> buildRequest(UUID_API_3));
                    return;
                } else if (statusCode != 200) {
                    long delay = switch (numberOfRequests) {
                        case 1 -> 50;
                        case 2, 3 -> 100;
                        case 4, 5 -> 400;
                        case 6, 7 -> 900;
                        default -> 1500;
                    };
                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> buildRequest(apiUrl));
                    return;
                }
                parseJson(response.body());
            }).exceptionally(ignored -> {
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> buildRequest(apiUrl));
                return null;
            });
        }
    }

    public void savePlayerImage() {
        String apiUrl = "https://mc-heads.net/body/";

        if (numberOfImageRequests == 2)
            apiUrl = "https://visage.surgeplay.com/full/432/";
        else if (numberOfImageRequests == 4)
            apiUrl = "https://render.crafty.gg/3d/full/";
        else if (numberOfImageRequests == 6)
            return;

        final String finalApiUrl = apiUrl + uuid;

        numberOfImageRequests++;

        String path = FabricLoader.getInstance().getGameDir() + "/cache/tiers/" + (regular ? "players/" : "");
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(Paths.get(path));
                URL uri = new URI(finalApiUrl).toURL();
                HttpURLConnection httpURLConnection = (HttpURLConnection) uri.openConnection();
                httpURLConnection.setRequestProperty("User-Agent", userAgent);
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setReadTimeout(5000);

                try (InputStream inputStream = httpURLConnection.getInputStream()) {
                    ImageIO.write(ImageIO.read(inputStream), "png", new File(path + uuid + ".png"));
                    imageSaved = numberOfImageRequests;
                }
            } catch (IOException | URISyntaxException ignored) {
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(this::savePlayerImage);
            }
        });
    }

    private void parseJson(String json) {
        if (JsonParser.parseString(json).isJsonNull()) {
            status = Status.API_ISSUE;
            return;
        }

        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

        if (jsonObject.has("code") && jsonObject.has("data") && jsonObject.has("success")) {
            if (!jsonObject.get("success").getAsString().contains("true")) {
                buildRequest(UUID_API_2);
                return;
            }
            JsonObject data = jsonObject.getAsJsonObject("data");
            if (data.has("player")) {
                JsonObject player = data.getAsJsonObject("player");
                if (player.has("username") && player.has("raw_id") && player.has("id")) {
                    name = player.get("username").getAsString();
                    uuid = player.get("raw_id").getAsString();
                }
            }
        } else if (jsonObject.has("name") && jsonObject.has("id")) {
            name = jsonObject.get("name").getAsString();
            uuid = jsonObject.get("id").getAsString();
        }

        if (uuid.isEmpty()) {
            status = Status.NOT_EXISTING;
            return;
        }

        if (!regular)
            savePlayerImage();

        updateTierlistProfiles(0);

        updateAppendingText();

        if (!inGameName.equalsIgnoreCase(name))
            nameChanged = true;

        status = Status.READY;
    }

    public void updateTierlistProfiles(int mode) {
        new Thread(() -> {
            String extra = "";
            if (forceNewRequest || mode != 0)
                extra = "?" + System.currentTimeMillis();

            forceNewRequest = false;

            switch (mode) {
                case 0:
                    profileMCTiers = new MCTiersProfile("https://mctiers.com/api/v2/profile/", uuid, extra);
                    profilePvPTiers = new PvPTiersProfile("https://pvptiers.com/api/profile/", uuid, extra);
                    profileSubtiers = new SubtiersProfile("https://subtiers.net/api/profile/", uuid, extra);
                    break;
                case 1:
                    profileMCTiers = new MCTiersProfile("https://mctiers.com/api/v2/profile/", uuid, extra);
                    break;
                case 2:
                    profilePvPTiers = new PvPTiersProfile("https://pvptiers.com/api/profile/", uuid, extra);
                    break;
                case 3:
                    profileSubtiers = new SubtiersProfile("https://subtiers.net/api/profile/", uuid, extra);
                    break;
            }

            updateAppendingText();

            if (mode != 0)
                TiersClient.showUpdatedPlayerProfile(this, false);
        }).start();
    }

    public void updateAppendingText() {
        toAppendRight = Component.empty();
        toAppendLeft = Component.empty();

        if (positionMCTiers == DisplayStatus.RIGHT)
            toAppendRight = updateProfileNameRight(profileMCTiers, activeMCTiersMode);
        else if (positionMCTiers == DisplayStatus.LEFT)
            toAppendLeft = updateProfileNameLeft(profileMCTiers, activeMCTiersMode);

        if (positionPvPTiers == DisplayStatus.RIGHT)
            toAppendRight = updateProfileNameRight(profilePvPTiers, activePvPTiersMode);
        else if (positionPvPTiers == DisplayStatus.LEFT)
            toAppendLeft = updateProfileNameLeft(profilePvPTiers, activePvPTiersMode);

        if (positionSubtiers == DisplayStatus.RIGHT)
            toAppendRight = updateProfileNameRight(profileSubtiers, activeSubtiersMode);
        else if (positionSubtiers == DisplayStatus.LEFT)
            toAppendLeft = updateProfileNameLeft(profileSubtiers, activeSubtiersMode);

        updateTextDisplayEntities();
    }

    public Component getFullName() {
        Component playerText = nameChanged ? Component.literal(inGameName + " (AKA " + name + ")") : Component.literal(name);

        if (!toggleMod)
            return playerText;

        updateAppendingText();
        return Component.empty()
                .append(toAppendLeft.copy())
                .append(playerText)
                .append(toAppendRight.copy());
    }

    public Component getFullName(Component original) {
        original = original.copy();

        if (status != Status.READY)
            return original;

        return fullName = Component.empty()
                .append(toAppendLeft.copy())
                .append(original)
                .append(toAppendRight.copy());
    }

    private Component updateProfileNameRight(SuperProfile superProfile, Mode activeMode) {
        MutableComponent returnValue = Component.empty();

        if (superProfile != null && superProfile.status == Status.READY) {
            GameMode shown = superProfile.getGameMode(activeMode);

            if ((shown == null || shown.status == Status.SEARCHING) || (shown.status == Status.NOT_EXISTING && displayMode == ModesTierDisplay.SELECTED))
                return returnValue;

            if (displayMode == ModesTierDisplay.ADAPTIVE_HIGHEST && shown.status == Status.NOT_EXISTING && superProfile.highest != null)
                shown = superProfile.highest;

            if (displayMode == ModesTierDisplay.HIGHEST && superProfile.highest != null && superProfile.highest.getTierPoints(false) > shown.getTierPoints(false))
                shown = superProfile.highest;

            if (shown == null || shown.status != Status.READY)
                return returnValue;

            MutableComponent separator = Component.literal(" | ").setStyle(toggleAdaptiveSeparator ? shown.displayedTier.getStyle() : Style.EMPTY.withColor(ColorControl.getColor("static_separator")));
            returnValue.append(Component.empty().append(separator).append(shown.displayedTier));

            if (toggleIcons)
                returnValue.append(Component.literal(" ").append(shown.gamemode.getIconTag()));
        }
        return returnValue;
    }

    private Component updateProfileNameLeft(SuperProfile superProfile, Mode activeMode) {
        MutableComponent returnValue = Component.empty();

        if (superProfile != null && superProfile.status == Status.READY) {
            GameMode shown = superProfile.getGameMode(activeMode);

            if ((shown == null || shown.status == Status.SEARCHING) || (shown.status == Status.NOT_EXISTING && displayMode == ModesTierDisplay.SELECTED))
                return returnValue;

            if (displayMode == ModesTierDisplay.ADAPTIVE_HIGHEST && shown.status == Status.NOT_EXISTING && superProfile.highest != null)
                shown = superProfile.highest;

            if (displayMode == ModesTierDisplay.HIGHEST && superProfile.highest != null && superProfile.highest.getTierPoints(false) > shown.getTierPoints(false))
                shown = superProfile.highest;

            if (shown == null || shown.status != Status.READY)
                return returnValue;

            MutableComponent separator = Component.literal(" | ").setStyle(toggleAdaptiveSeparator ? shown.displayedTier.getStyle() : Style.EMPTY.withColor(ColorControl.getColor("static_separator")));

            if (toggleIcons)
                returnValue = Component.empty().append(shown.gamemode.getIconTag()).append(" ");
            returnValue.append(Component.empty().append(shown.displayedTier).append(separator));
        }
        return returnValue;
    }

    public void resetDrawnStatus() {
        if (profileMCTiers == null || profilePvPTiers == null || profileSubtiers == null)
            return;
        profileMCTiers.drawn = false;
        profilePvPTiers.drawn = false;
        profileSubtiers.drawn = false;
        for (GameMode mode : profileMCTiers.gameModes)
            mode.drawn = false;
        for (GameMode mode : profilePvPTiers.gameModes)
            mode.drawn = false;
        for (GameMode mode : profileSubtiers.gameModes)
            mode.drawn = false;
    }

    public boolean isPlayerValid() {
        if (status == Status.NOT_EXISTING) {
            sendMessageToPlayer(Icons.colorText(name + " was not found or isn't a premium account", "red"), false);
            return false;
        } else if (status == Status.NOT_PLAYER) {
            sendMessageToPlayer(Icons.colorText("Not a valid player name", "red"), false);
            return false;
        } else if (status == Status.TIMEOUTED) {
            sendMessageToPlayer(Icons.colorText(name + "'s search was timeouted. Clear cache and retry", "red"), false);
            return false;
        } else if (status == Status.API_ISSUE) {
            sendMessageToPlayer(Icons.colorText(name + "'s search failed: API Issue. Update Tiers or retry in a while", "red"), false);
            return false;
        }
        return true;
    }

    public Component deepReplace(Component original) {
        String targetName = nameChanged ? inGameName : name;

        Style originalStyle = original.getStyle();
        MutableComponent newText;
        ComponentContents content = original.getContents();

        if (content instanceof PlainTextContents plain) {
            String string = plain.text();

            if (string.contains(targetName)) {
                newText = Component.empty();
                int lastIndex = 0;
                int index;

                while ((index = string.indexOf(targetName, lastIndex)) != -1) {
                    if (index > lastIndex)
                        newText.append(Component.literal(string.substring(lastIndex, index)).setStyle(originalStyle));

                    MutableComponent namePart = Component.literal(targetName).setStyle(originalStyle);
                    newText.append(getFullName(namePart));

                    lastIndex = index + targetName.length();
                }

                if (lastIndex < string.length())
                    newText.append(Component.literal(string.substring(lastIndex)).setStyle(originalStyle));
            } else {
                newText = Component.literal(string).setStyle(originalStyle);
            }
        } else if (content instanceof TranslatableContents translatableTextContent) {
            Object[] args = translatableTextContent.getArgs();
            Object[] newArgs = new Object[args.length];

            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component text)
                    newArgs[i] = deepReplace(text);
                else if (args[i] instanceof String string)
                    newArgs[i] = deepReplace(Component.literal(string).setStyle(originalStyle));
                else
                    newArgs[i] = args[i];
            }
            newText = Component.translatable(translatableTextContent.getKey(), newArgs).setStyle(originalStyle);
        } else {
            newText = original.plainCopy().setStyle(originalStyle);
        }

        for (Component sibling : original.getSiblings())
            newText.append(deepReplace(sibling));

        return deepReplaceName = newText;
    }

    @Override
    public String toString() {
        return name + "'sPlayerProfile{" +
                "\nstatus=" + status +
                "\nimageSaved=" + imageSaved +
                "\nnumberOfImageRequests=" + numberOfImageRequests +
                "\ninGameName=" + (inGameName != null ? inGameName : "null") +
                "\nnameChanged=" + nameChanged +
                "\nuuid=" + (uuid != null ? uuid : "null") +
                "\ntoAppendLeft=" + (toAppendLeft != null ? toAppendLeft.getString() : "null") +
                "\ntoAppendRight=" + (toAppendRight != null ? toAppendRight.getString() : "null") +
                "\nfullName=" + (fullName != null ? fullName.getString() : "null") +
                "\ndeepReplaceName=" + (deepReplaceName != null ? deepReplaceName.getString() : "null") +
                "\nnumberOfRequests=" + numberOfRequests +
                "\nregular=" + regular +
                "\n\nprofileMCTiers=" + (profileMCTiers != null ? profileMCTiers : "null") +
                "\n\nprofilePvPTiers=" + (profilePvPTiers != null ? profilePvPTiers : "null") +
                "\n\nprofileSubtiers=" + (profileSubtiers != null ? profileSubtiers : "null") +
                "}\n\n\n--- NEXT ---\n\n\n";
    }
}