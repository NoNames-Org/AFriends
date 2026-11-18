package dev.no.name.afriends.net.game;

import dev.no.name.afriends.net.service.NameProof;
import dev.no.name.afriends.net.service.PlayerAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

// Игрок для сети: ник из лаунчера и способ доказать, что ник наш
final class GameAccount {
    private static final int MIN_TOKEN = 32;

    private GameAccount() {
    }

    static PlayerAccount current() {
        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        return new PlayerAccount(user.getName(), proof(minecraft, user));
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
