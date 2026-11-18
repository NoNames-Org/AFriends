package dev.no.name.afriends.net.game.friends;

import dev.no.name.afriends.api.AFriendsAddon;
import dev.no.name.afriends.api.AFriendsApi;
import dev.no.name.afriends.net.game.LinkSettings;
import dev.no.name.afriends.net.service.LinkService;

// Сеть встраивается в окна друзей тем же API, что и чужие дополнения, только первой:
// для друга по сети её строка статуса важнее, чем у остальных
public final class NetworkAddon implements AFriendsAddon {
    private final FriendSync friends;
    private final LinkService link;
    private final LinkSettings settings;

    public NetworkAddon(FriendSync friends, LinkService link, LinkSettings settings) {
        this.friends = friends;
        this.link = link;
        this.settings = settings;
    }

    @Override
    public void register(AFriendsApi api) {
        api.addFriendStatus(new NetworkFriendStatus(friends));
        api.addFriendRequests(new NetworkRequests(friends));
        api.addFriendListener(new NetworkFriendEvents(friends));
        api.setProfileStatus(new NetworkProfile(friends, settings));
        api.addFriendAction(NetworkActions.join(friends));
        api.addFriendAction(NetworkActions.safetyNumber(friends, link));
    }
}
