package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.net.model.ClientKind;
import dev.no.name.blooddesigner.net.service.NameProof;
import dev.no.name.blooddesigner.net.service.PlayerAccount;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

// Игрок для сети: ник из лаунчера и мод, которым представляемся. SMPHelper встраивает BloodDesigner,
// поэтому если он загружен — игрок с SMPHelper и у него значок S
final class GameAccount {
    private static final int MIN_TOKEN = 32;

    private GameAccount() {
    }

    static PlayerAccount current() {
        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        ClientKind client = FabricLoader.getInstance().isModLoaded("smphelper") ? ClientKind.SMPHELPER : ClientKind.BLOODDESIGNER;
        return new PlayerAccount(user.getName(), client, proof(minecraft, user));
    }

    // С пиратским или офлайн-аккаунтом Mojang откажет — ник просто останется неподтверждённым
    private static NameProof proof(Minecraft minecraft, User user) {
        return challenge -> {
            String token = user.getAccessToken();
            if (token == null || token.length() < MIN_TOKEN) {
                throw new IllegalStateException("нет токена Mojang");
            }
            minecraft.services().sessionService().joinServer(user.getProfileId(), token, challenge);
        };
    }
}
