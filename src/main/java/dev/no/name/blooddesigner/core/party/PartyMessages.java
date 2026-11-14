package dev.no.name.blooddesigner.core.party;

import dev.no.name.blooddesigner.core.ServerMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyMessages {
    private static final String NAME = "([A-Za-z0-9_]{3,16})";
    private static final Pattern LEADER = Pattern.compile("^Leader: " + NAME + "$");
    private static final Pattern MEMBERS = Pattern.compile("^Members: \\[(.*)]$");
    private static final Pattern INVITED = Pattern.compile("^" + NAME + " has been invited to the party!?$");
    private static final Pattern INCOMING = Pattern.compile("^" + NAME + " invited you to their party!?$");
    private static final Pattern PARTY_WORD = Pattern.compile("\\bpart(y|ies)\\b", Pattern.CASE_INSENSITIVE);

    private static final String ACCEPT_PREFIX = "party accept";

    private PartyMessages() {
    }

    public static String acceptCommand(List<String> clickCommands) {
        for (String command : clickCommands) {
            String stripped = command.strip();
            if (stripped.startsWith("/")) {
                stripped = stripped.substring(1);
            }
            if (stripped.toLowerCase(Locale.ROOT).startsWith(ACCEPT_PREFIX)) {
                return stripped;
            }
        }
        return null;
    }

    public static PartyEvent parse(String plain) {
        if (!ServerMessages.isBlock(plain)) {
            return null;
        }
        List<String> lines = ServerMessages.lines(plain);
        if (lines.isEmpty()) {
            return null;
        }
        PartyEvent info = parseInfo(lines);
        if (info != null) {
            return info;
        }
        String first = lines.get(0);
        Matcher matcher = INVITED.matcher(first);
        if (matcher.matches()) {
            return new PartyEvent.Invited(matcher.group(1));
        }
        matcher = INCOMING.matcher(first);
        if (matcher.matches()) {
            return new PartyEvent.IncomingInvite(matcher.group(1));
        }
        String lower = first.toLowerCase(Locale.ROOT);
        if (lower.startsWith("you left from the party")) {
            return new PartyEvent.Left();
        }
        if (lower.startsWith("the party was disbanded")) {
            return new PartyEvent.Disbanded();
        }
        if (lower.startsWith("you are not at a party") || lower.startsWith("you are not in a party")) {
            return new PartyEvent.NotInParty();
        }
        if (lower.startsWith("you can't invite yourself")) {
            return new PartyEvent.InviteSelf();
        }
        String text = String.join(" ", lines);
        return PARTY_WORD.matcher(text).find() ? new PartyEvent.Other(text) : null;
    }

    private static PartyEvent parseInfo(List<String> lines) {
        if (lines.size() < 2) {
            return null;
        }
        Matcher leader = LEADER.matcher(lines.get(0));
        Matcher members = MEMBERS.matcher(lines.get(1));
        if (!leader.matches() || !members.matches()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String name : members.group(1).split(",")) {
            if (!name.isBlank()) {
                names.add(name.strip());
            }
        }
        return new PartyEvent.Info(leader.group(1), names);
    }
}
