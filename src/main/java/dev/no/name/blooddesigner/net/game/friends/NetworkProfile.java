package dev.no.name.blooddesigner.net.game.friends;

import dev.no.name.blooddesigner.api.ProfileStatus;
import dev.no.name.blooddesigner.net.game.LinkSettings;

// свой статус для друзей по сети, под ником в шапке друзей
final class NetworkProfile implements ProfileStatus {
    private final FriendSync friends;
    private final LinkSettings settings;

    NetworkProfile(FriendSync friends, LinkSettings settings) {
        this.friends = friends;
        this.settings = settings;
    }

    @Override
    public boolean active() {
        return friends.active();
    }

    @Override
    public String get() {
        return settings.status();
    }

    @Override
    public void set(String status) {
        settings.setStatus(status);
    }
}
