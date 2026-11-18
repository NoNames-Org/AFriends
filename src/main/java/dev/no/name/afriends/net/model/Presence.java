package dev.no.name.afriends.net.model;

// где сейчас друг и что у него в статусе; приходит зашифрованным, расшифровывается только у друзей
public record Presence(Where where, String server, String status, long since) {
    public static final int MAX_STATUS = 40;
    public static final int MAX_SERVER = 64;

    public enum Where {
        // в главном меню или на экране загрузки
        MENU,
        // одиночный мир (или открыт в LAN)
        SINGLEPLAYER,
        // сервер, адрес в server
        SERVER,
        // друг выключил «показывать сервер»: видно только что он в игре
        HIDDEN
    }

    public Presence {
        server = server == null ? "" : clip(server, MAX_SERVER);
        status = status == null ? "" : clip(status, MAX_STATUS);
    }

    // свой статус, как его увидят друзья: без управляющих символов и не длиннее MAX_STATUS
    public static String cleanStatus(String text) {
        return text == null ? "" : clip(text, MAX_STATUS);
    }

    public boolean joinable() {
        return where == Where.SERVER && !server.isEmpty();
    }

    // то же место и та же подпись: друзьям слать нечего
    public boolean sameAs(Presence other) {
        return other != null && where == other.where && server.equals(other.server) && status.equals(other.status);
    }

    private static String clip(String text, int max) {
        String clean = text.replaceAll("[\\p{Cntrl}§]", "").strip();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
