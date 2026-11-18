package dev.no.name.afriends.net.chat;

import dev.no.name.afriends.core.GroupPermissions;
import dev.no.name.afriends.core.MessageImage;

/** Какое право нужно участнику группы на этот конверт. Голоса в опросах и отметки прав не требуют. */
public final class GroupRights {
    private GroupRights() {
    }

    public static boolean allows(int perms, ChatPayload payload) {
        return switch (payload) {
            case ChatPayload.Text text when text.poll() != null -> GroupPermissions.has(perms, GroupPermissions.POLLS);
            case ChatPayload.Text text -> canSend(perms, text.image());
            case ChatPayload.GroupName ignored -> GroupPermissions.has(perms, GroupPermissions.TITLE);
            case ChatPayload.GroupMeta meta -> (meta.name() == null || GroupPermissions.has(perms, GroupPermissions.TITLE))
                    && (meta.description() == null || GroupPermissions.has(perms, GroupPermissions.DESCRIPTION))
                    && (meta.avatar() == null || GroupPermissions.has(perms, GroupPermissions.AVATAR));
            case null, default -> true;
        };
    }

    /** Можно ли отправить сообщение с этим вложением (null — просто текст). */
    public static boolean canSend(int perms, MessageImage attachment) {
        if (attachment == null) {
            return GroupPermissions.has(perms, GroupPermissions.SEND);
        }
        if (attachment.voice()) {
            return GroupPermissions.has(perms, GroupPermissions.VOICE);
        }
        return GroupPermissions.has(perms, attachment.animated() ? GroupPermissions.GIFS : GroupPermissions.IMAGES);
    }
}
