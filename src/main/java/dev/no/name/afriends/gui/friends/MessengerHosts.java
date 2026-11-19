package dev.no.name.afriends.gui.friends;

import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.core.MessageReply;
import dev.no.name.afriends.core.PrivateMessage;
import dev.no.name.afriends.core.PrivateMessages;
import dev.no.name.afriends.gui.widget.ConfirmBox;
import net.minecraft.client.Minecraft;

import java.util.List;

import static dev.no.name.afriends.gui.friends.MessengerLayout.HEIGHT;
import static dev.no.name.afriends.gui.friends.MessengerLayout.WIDTH;

/** Что окошки мессенджера (профиль, редактор группы, действия с сообщением) просят у окна. */
final class MessengerHosts {
    private MessengerHosts() {
    }

    /** Окно профиля. */
    static final class ProfileHost implements ProfilePanel.Host {
        private final MessengerPopups popups;
        private final ChatController controller;
        private final ChatNavigator navigator;

        ProfileHost(MessengerPopups popups, ChatController controller, ChatNavigator navigator) {
            this.popups = popups;
            this.controller = controller;
            this.navigator = navigator;
        }

        @Override
        public void confirm(ConfirmBox.Request request) {
            popups.confirm(request);
        }

        @Override
        public void view(MessageImage image) {
            popups.viewer().open(image);
        }

        @Override
        public void invite(String chat) {
            popups.profilePanel().close();
            popups.openGroupEditor(() -> popups.openInvite(chat));
        }

        @Override
        public void write() {
            popups.profilePanel().close();
            navigator.focusInput();
        }

        @Override
        public void warn(String text) {
            controller.warn(text);
        }

        @Override
        public void info(String text) {
            controller.info(text);
        }

        @Override
        public void jumpTo(String messageKey) {
            popups.profilePanel().close();
            controller.requestJump(messageKey);
        }
    }

    /** Окно группы: всё уходит в сеть, ответ — на потоке игры. */
    static final class GroupHost implements GroupEditor.Host {
        private final MessengerPopups popups;
        private final ChatController controller;

        GroupHost(MessengerPopups popups, ChatController controller) {
            this.popups = popups;
            this.controller = controller;
        }

        @Override
        public void create(List<String> friends, String title) {
            controller.groups().create(friends, title, chat -> popups.groupCreated(chat, title), controller::warn);
        }

        @Override
        public void add(String chat, String friend) {
            controller.groups().add(chat, friend, controller::warn);
            controller.info(friend + " добавлен в группу");
        }

        @Override
        public void kick(String chat, String member) {
            popups.confirm(new ConfirmBox.Request("Исключить " + member + " из группы?", null, false, "Исключить", true,
                    checked -> controller.groups().kick(chat, member, controller::warn)));
        }

        @Override
        public void leave(String chat) {
            popups.confirmLeave(chat, controller.chatName(chat));
        }

        @Override
        public void rename(String chat, String title) {
            controller.groups().rename(chat, title, controller::warn);
        }
    }

    /** Действия с сообщением: ответ, пересылка, выделение, подтверждение удаления. */
    static final class ActionsHost implements MessageActions.Host {
        private final MessengerPopups popups;
        private final ChatController controller;
        private final PrivateMessages messages;
        private final ComposerView composer;
        private final Selection selection;
        private final ChatNavigator navigator;

        ActionsHost(MessengerPopups popups, ChatController controller, PrivateMessages messages, ComposerView composer,
                    Selection selection, ChatNavigator navigator) {
            this.popups = popups;
            this.controller = controller;
            this.messages = messages;
            this.composer = composer;
            this.selection = selection;
            this.navigator = navigator;
        }

        @Override
        public String partner() {
            return controller.partner();
        }

        @Override
        public String partnerName() {
            if (controller.isNotes()) {
                return Minecraft.getInstance().getUser().getName();
            }
            return controller.isOfficial() ? PrivateMessages.OFFICIAL_NAME : messages.displayName(controller.partner());
        }

        @Override
        public boolean editable() {
            return controller.partner() != null && !controller.isReadOnly();
        }

        @Override
        public void reply(MessageReply reply) {
            composer.replyStrip().start(reply);
            navigator.focusInput();
        }

        @Override
        public void forward(List<PrivateMessage> forwarded) {
            popups.forwardPicker().open(forwarded, controller.forwardTargets(), WIDTH, HEIGHT);
        }

        @Override
        public void select(PrivateMessage message) {
            selection.start(message);
        }

        @Override
        public void confirm(ConfirmBox.Request request) {
            popups.confirm(request);
        }

        @Override
        public void deleted() {
            selection.clear();
        }
    }
}
