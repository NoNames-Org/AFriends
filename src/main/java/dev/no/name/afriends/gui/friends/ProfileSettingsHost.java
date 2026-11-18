package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.MessageRoute;
import dev.no.name.afriends.core.ProfileRoute;

import java.util.function.Consumer;

/** Настройки мессенджера, которые живут на сервере сети: кто может писать. */
final class ProfileSettingsHost implements MessengerSettingsPanel.Host {
    private final MessageRoute route;
    private final Consumer<String> problem;

    ProfileSettingsHost(MessageRoute route, Consumer<String> problem) {
        this.route = route;
        this.problem = problem;
    }

    private ProfileRoute profile() {
        return route.profile();
    }

    @Override
    public boolean available() {
        return profile() != null && profile().available();
    }

    @Override
    public boolean dmFromEveryone() {
        return available() && profile().dmFromEveryone();
    }

    @Override
    public void setDmFromEveryone(boolean everyone) {
        if (available()) {
            profile().setDmFromEveryone(everyone, problem);
        }
    }
}
