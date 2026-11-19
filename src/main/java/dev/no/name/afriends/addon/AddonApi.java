package dev.no.name.afriends.addon;

import dev.no.name.afriends.api.AFriendsApi;
import dev.no.name.afriends.api.ComposeAction;
import dev.no.name.afriends.api.FriendAction;
import dev.no.name.afriends.api.FriendListener;
import dev.no.name.afriends.api.FriendRequests;
import dev.no.name.afriends.api.FriendStatus;
import dev.no.name.afriends.api.Friends;
import dev.no.name.afriends.api.MessageCard;
import dev.no.name.afriends.api.Privacy;
import dev.no.name.afriends.api.ProfileStatus;
import dev.no.name.afriends.api.RailPanel;
import dev.no.name.afriends.api.SendCheck;
import dev.no.name.afriends.api.SettingsCategory;
import dev.no.name.afriends.core.FriendManager;
import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.ServerScope;
import dev.no.name.afriends.core.SkinMemory;
import dev.no.name.afriends.core.SocialData;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;
import java.util.Optional;

/**
 * То, что видит аддон в {@code register(api)}: регистрация уходит в {@link AddonRegistry},
 * а данные и действия берутся у переписки и сети. Публичный контракт — {@link AFriendsApi}, он не меняется
 */
public final class AddonApi implements AFriendsApi {
    private final AddonRegistry registry;
    private final SocialData social;
    private final Runnable save;
    private final MessageRoute route;
    private final SkinMemory skins;

    public AddonApi(AddonRegistry registry, SocialData social, Runnable save, MessageRoute route, SkinMemory skins) {
        this.registry = registry;
        this.social = social;
        this.save = save;
        this.route = route;
        this.skins = skins;
    }

    @Override
    public void addRailPanel(RailPanel panel) {
        registry.addRailPanel(panel);
    }

    @Override
    public void addSettingsCategory(SettingsCategory category) {
        registry.addSettingsCategory(category);
    }

    @Override
    public void addFriendAction(FriendAction action) {
        registry.addFriendAction(action);
    }

    @Override
    public void addComposeAction(ComposeAction action) {
        registry.addComposeAction(action);
    }

    @Override
    public void addMessageCard(MessageCard card) {
        registry.addMessageCard(card);
    }

    @Override
    public void addFriendStatus(FriendStatus status) {
        registry.addFriendStatus(status);
    }

    @Override
    public void addFriendRequests(FriendRequests requests) {
        registry.addFriendRequests(requests);
    }

    @Override
    public void setProfileStatus(ProfileStatus status) {
        registry.setProfileStatus(status);
    }

    @Override
    public void setPrivacy(Privacy privacy) {
        registry.setPrivacy(privacy);
    }

    @Override
    public void addSendCheck(SendCheck check) {
        registry.addSendCheck(check);
    }

    @Override
    public void addFriendListener(FriendListener listener) {
        registry.addFriendListener(listener);
    }

    @Override
    public Friends friends() {
        return new CurrentFriends();
    }

    @Override
    public Optional<String> sendMessage(String partner, String text) {
        return route.send(partner, text, problem -> { });
    }

    @Override
    public boolean viaNetwork(String partner) {
        return route.viaNetwork(partner);
    }

    // друзья текущего сервера; в главном меню (без сервера) — никого
    private FriendManager current() {
        return ServerScope.NONE.equals(ServerScope.current()) ? null : social.friends();
    }

    private final class CurrentFriends implements Friends {
        @Override
        public List<String> names() {
            FriendManager friends = current();
            return friends == null ? List.of() : friends.names();
        }

        @Override
        public boolean contains(String name) {
            FriendManager friends = current();
            return friends != null && friends.find(name) != null;
        }

        @Override
        public boolean add(String name) {
            FriendManager friends = current();
            if (friends == null || friends.add(name) != FriendManager.AddResult.ADDED) {
                return false;
            }
            save.run();
            return true;
        }

        @Override
        public boolean online(String name) {
            FriendStatus.Status status = registry.status(name);
            return status != null && status.online() || nearby(name);
        }

        @Override
        public boolean nearby(String name) {
            return FriendManager.onlinePlayerNames().stream().anyMatch(player -> player.equalsIgnoreCase(name));
        }

        @Override
        public PlayerSkin skin(String name) {
            FriendManager friends = current();
            return friends != null ? friends.skinOf(name) : skins.skin(name);
        }

        @Override
        public List<String> nearbyNames() {
            return FriendManager.onlinePlayerNames();
        }
    }
}
