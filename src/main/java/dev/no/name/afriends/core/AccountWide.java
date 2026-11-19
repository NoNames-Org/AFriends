package dev.no.name.afriends.core;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Изменения, которые приходят по сети и касаются аккаунта целиком, а не одного сервера:
 * сообщение могло уйти с любого сервера, поэтому ищем и меняем его во всех переписках аккаунта
 */
public final class AccountWide {
    private final Supplier<List<SocialData.Scope>> scopes;

    AccountWide(Supplier<List<SocialData.Scope>> scopes) {
        this.scopes = scopes;
    }

    // реакция собеседника: true — сообщение нашлось
    public boolean react(String partner, String messageId, boolean mine, String reaction) {
        for (PrivateMessages messages : messages()) {
            if (messages.react(partner, messageId, mine, reaction)) {
                return true;
            }
        }
        return false;
    }

    // голос в опросе группы; voter null — свой
    public boolean vote(String chat, String messageId, String voter, int choice) {
        for (PrivateMessages messages : messages()) {
            if (messages.vote(chat, messageId, voter, choice)) {
                return true;
            }
        }
        return false;
    }

    // чат пропадает на всех серверах этого аккаунта (вышел из группы)
    public void deleteChat(String chat) {
        messages().forEach(messages -> messages.delete(chat));
    }

    // собеседник удалил сообщения у обоих
    public void remove(String partner, Collection<String> messageIds) {
        messages().forEach(messages -> messages.remove(partner, messageIds));
    }

    // отметка на своё сообщение
    public void receipt(List<String> messageIds, int state) {
        messages().forEach(messages -> messageIds.forEach(id -> messages.markReceipt(id, state)));
    }

    // собеседник закрепил или открепил сообщение у обоих
    public void pin(String partner, String messageId, boolean pinned) {
        messages().forEach(messages -> messages.pin(partner, messageId, pinned));
    }

    // друг удалил нас в сети: на каждом сервере этого аккаунта он больше не друг. true — где-то был
    public boolean removeFriend(String name) {
        boolean removed = false;
        for (SocialData.Scope scope : scopes.get()) {
            removed |= scope.friends().remove(name);
        }
        return removed;
    }

    private List<PrivateMessages> messages() {
        return scopes.get().stream().map(SocialData.Scope::messages).toList();
    }
}
