package dev.no.name.blooddesigner.core;

import dev.no.name.blooddesigner.addon.Addons;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

import static dev.no.name.blooddesigner.compat.ClientCmd.argument;
import static dev.no.name.blooddesigner.compat.ClientCmd.literal;

public final class FriendCommands {
    private static final String PREFIX = "[Друзья] ";
    private static final String NAME_ARG = "name";

    private final SocialData social;
    private final Runnable save;

    public FriendCommands(SocialData social, Runnable save) {
        this.social = social;
        this.save = save;
    }

    public void register() {
        SuggestionProvider<FabricClientCommandSource> onlinePlayers = (context, builder) ->
                SharedSuggestionProvider.suggest(FriendManager.onlinePlayerNames().stream()
                        .filter(name -> social.friends().find(name) == null), builder);
        SuggestionProvider<FabricClientCommandSource> friendNames = (context, builder) ->
                SharedSuggestionProvider.suggest(social.friends().names(), builder);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                literal("friend")
                        .then(literal("add").then(argument(NAME_ARG, StringArgumentType.word())
                                .suggests(onlinePlayers).executes(this::add)))
                        .then(literal("remove").then(argument(NAME_ARG, StringArgumentType.word())
                                .suggests(friendNames).executes(this::remove)))
                        .then(literal("list").executes(this::list))
                        .executes(this::list)));
    }

    private int add(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, NAME_ARG);
        switch (social.friends().add(name)) {
            case ADDED -> {
                save.run();
                reply(context, name + " добавлен в друзья", ChatFormatting.GREEN);
                Addons.get().added(name, notice -> reply(context, notice, ChatFormatting.GRAY));
                return 1;
            }
            case ALREADY -> reply(context, name + " уже в друзьях", ChatFormatting.YELLOW);
            case SELF -> reply(context, "Себя добавить нельзя", ChatFormatting.YELLOW);
            case INVALID -> reply(context, "Ник «" + name + "» не похож на ник Minecraft", ChatFormatting.RED);
        }
        return 0;
    }

    private int remove(CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, NAME_ARG);
        if (!social.friends().remove(name)) {
            reply(context, name + " нет в друзьях", ChatFormatting.YELLOW);
            return 0;
        }
        save.run();
        Addons.get().removed(name);
        reply(context, name + " удалён из друзей", ChatFormatting.GREEN);
        return 1;
    }

    private int list(CommandContext<FabricClientCommandSource> context) {
        List<Friend> sorted = social.friends().sorted();
        if (sorted.isEmpty()) {
            reply(context, "Друзей пока нет. Добавь: /friend add <ник>", ChatFormatting.GRAY);
            return 1;
        }
        reply(context, "Друзья (" + sorted.size() + "):", ChatFormatting.WHITE);
        for (Friend friend : sorted) {
            context.getSource().sendFeedback(Component.literal(" • " + friend.name()).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static void reply(CommandContext<FabricClientCommandSource> context, String text, ChatFormatting color) {
        context.getSource().sendFeedback(Component.literal(PREFIX).withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(text).withStyle(color)));
    }
}
