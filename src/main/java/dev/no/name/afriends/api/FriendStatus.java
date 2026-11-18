package dev.no.name.afriends.api;

// что показать у друга в списке. null — дополнению про этого друга сказать нечего
@FunctionalInterface
public interface FriendStatus {
    Status status(String name);

    // online — зелёная точка и подъём наверх списка; line — строка под ником (null — своя строка AFriends);
    // note — короткая подпись после ника; reachable — можно написать в ЛС и позвать в пати (игрок на этом же сервере)
    record Status(boolean online, String line, String note, boolean reachable) {
    }
}
