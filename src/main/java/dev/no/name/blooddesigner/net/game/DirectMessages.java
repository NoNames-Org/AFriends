package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.addon.Addons;
import dev.no.name.blooddesigner.core.MessageRoute;
import dev.no.name.blooddesigner.core.PrivateMessage;
import dev.no.name.blooddesigner.core.PrivateMessages;
import dev.no.name.blooddesigner.core.SocialData;
import dev.no.name.blooddesigner.net.model.Connection;
import dev.no.name.blooddesigner.net.model.LinkFriend;
import dev.no.name.blooddesigner.net.model.LinkSnapshot;
import dev.no.name.blooddesigner.net.service.LinkService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.Optional;
import java.util.function.Consumer;

// Куда уходит ЛС: другу по сети — сквозным шифрованием через свой сервер, игроку без мода — по-старому /msg.
// Всё на потоке игры
public final class DirectMessages implements MessageRoute {
    private final SocialData social;
    private final LinkService link;

    DirectMessages(SocialData social, LinkService link) {
        this.social = social;
        this.link = link;
    }

    // друг по сети: пишется через сервер сети, и неважно, на каком сервере Minecraft он сейчас
    @Override
    public boolean viaNetwork(String partner) {
        return partner != null && link.snapshot().friendNamed(partner) != null;
    }

    // Отправить. Пусто — ушло в обработку; иначе причина, почему не ушло сразу.
    // Если сеть откажет позже, причина придёт в failed, а сообщение будет помечено неотправленным
    @Override
    public Optional<String> send(String partner, String text, Consumer<String> failed) {
        LinkSnapshot state = link.snapshot();
        // BloodDesigner: ответ админам мода через сервер сети, на портал
        if (PrivateMessages.isOfficial(partner)) {
            if (state.connection() != Connection.ONLINE) {
                return Optional.of("Нет связи с сетью BloodDesigner — включи сеть в настройках");
            }
            link.sendOfficial(text).thenAccept(code -> Minecraft.getInstance().execute(() -> {
                if ("ok".equals(code)) {
                    social.messages().add(partner, new PrivateMessage(true, text, System.currentTimeMillis()));
                } else {
                    failed.accept(officialProblem(code));
                }
            }));
            return Optional.empty();
        }
        LinkFriend friend = state.friendNamed(partner);
        if (friend != null) {
            if (state.connection() != Connection.ONLINE) {
                return Optional.of(LinkText.sendProblem("offline"));
            }
            link.sendText(friend.id(), text).thenAccept(code -> Minecraft.getInstance().execute(() -> {
                long now = System.currentTimeMillis();
                if ("ok".equals(code)) {
                    social.messages().add(partner, new PrivateMessage(true, text, now));
                } else {
                    social.messages().markOffline(partner, now);
                    failed.accept(LinkText.sendProblem(code));
                }
            }));
            return Optional.empty();
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return Optional.of("Нет соединения с сервером");
        }
        String command = "msg " + partner + " " + text;
        Optional<String> problem = Addons.get().sendProblem(partner, command);
        if (problem.isEmpty()) {
            connection.sendCommand(command);
        }
        return problem;
    }

    private static String officialProblem(String code) {
        return switch (code) {
            case "rate_limited" -> "Слишком часто — подожди минуту";
            case "too_many" -> "Админы ещё не прочитали прошлые сообщения";
            case "bad_text" -> "Слишком длинное сообщение";
            default -> LinkText.sendProblem(code);
        };
    }

    // строка под ником в шапке переписки; null — не друг по сети, у BloodDesigner своя подпись
    @Override
    public String headerLine(String partner) {
        LinkFriend friend = link.snapshot().friendNamed(partner);
        if (friend == null) {
            return null;
        }
        String where = friend.online() ? LinkText.line(friend, System.currentTimeMillis()) : "Не в сети — прочитает при входе";
        return where + " · сквозное шифрование";
    }
}
