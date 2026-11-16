package dev.no.name.blooddesigner.net.game.friends;

import dev.no.name.blooddesigner.gui.friends.SafetyScreen;
import dev.no.name.blooddesigner.api.FriendAction;
import dev.no.name.blooddesigner.compat.Compat;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.net.game.LinkJoin;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.service.LinkService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// пункты меню друга по сети
final class NetworkActions {
    private NetworkActions() {
    }

    // друг на другом сервере: перейти к нему
    static FriendAction join(FriendSync friends) {
        return new FriendAction(Icon.LOG_IN, name -> "Зайти к другу",
                name -> {
                    LinkFriend friend = friends.friend(name);
                    if (friend != null && friend.online()) {
                        LinkJoin.join(friend.presence());
                    }
                },
                name -> {
                    LinkFriend friend = friends.active() ? friends.friend(name) : null;
                    return friend != null && friend.online() && LinkJoin.canJoin(friend.presence());
                },
                false);
    }

    // Отпечаток пары: 60 цифр, одинаковые у обоих. Совпали — между вами никого, даже сервера сети
    static FriendAction safetyNumber(FriendSync friends, LinkService link) {
        return new FriendAction(Icon.SHIELD_CHECK, name -> "Сверить ключи",
                name -> {
                    LinkFriend friend = friends.friend(name);
                    if (friend == null) {
                        return;
                    }
                    link.safetyNumber(friend.id()).thenAccept(number -> Minecraft.getInstance().execute(() -> {
                        if (number == null) {
                            return;
                        }
                        Compat.setScreen(new SafetyScreen(Compat.currentScreen(), friend.name(), number));
                    }));
                },
                name -> friends.friend(name) != null,
                false);
    }
}
