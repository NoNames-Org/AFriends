package dev.no.name.afriends.gui;

import dev.no.name.afriends.addon.AddonRegistry;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.gui.media.ChatMedia;
import dev.no.name.afriends.gui.media.GifFavorites;
import dev.no.name.afriends.gui.media.ImageTextures;
import dev.no.name.afriends.gui.media.VoiceClips;
import dev.no.name.afriends.net.announcements.AnnouncementFeed;
import dev.no.name.afriends.net.update.VersionCheck;

/**
 * Общие сервисы окон AFriends: то, что раньше лежало в синглтонах. Создаются один раз в корне сборки
 * ({@code AFriendsClient}) и передаются в окна явно
 */
public record UiServices(AddonRegistry addons, ImageTextures images, GifFavorites gifs, VoiceClips voices,
                         AnnouncementFeed announcements, VersionCheck versions) {

    /** Медиа переписки, которые качаются через route. */
    public ChatMedia media(MessageRoute route) {
        return new ChatMedia(route, images, voices);
    }
}
