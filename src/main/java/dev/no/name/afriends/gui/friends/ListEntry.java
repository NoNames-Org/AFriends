package dev.no.name.afriends.gui.friends;

/** Строка списка чатов: сам чат или служебная строка («Архив», «← Назад», «Написать нику»). */
sealed interface ListEntry {
    /** Чат «Объявления»: всегда первый, только для чтения. */
    ListEntry ANNOUNCEMENTS = new Announcements();
    ListEntry ARCHIVE = new ArchiveRow();
    ListEntry ARCHIVE_BACK = new ArchiveBack();

    /** Переписка; chat — строка чата, как её отдаёт PrivateMessages. */
    record Chat(String chat) implements ListEntry {
    }

    record Announcements() implements ListEntry {
    }

    /** «Архив» — открыть архивные чаты. */
    record ArchiveRow() implements ListEntry {
    }

    /** «← Назад» из архива ко всем чатам. */
    record ArchiveBack() implements ListEntry {
    }

    /** Ник из поиска, с которым ещё нет переписки: «Написать сообщение». */
    record WriteTo(String name) implements ListEntry {
    }

    /** Служебная строка: значок и подпись вместо чата. */
    default boolean isServiceRow() {
        return this instanceof ArchiveRow || this instanceof ArchiveBack || this instanceof WriteTo;
    }

    /** Строка чата, которую выбирают и открывают; null у служебных строк. */
    default String chat() {
        return switch (this) {
            case Chat chat -> chat.chat();
            case Announcements announcements -> ChatController.ANNOUNCEMENTS;
            default -> null;
        };
    }
}
