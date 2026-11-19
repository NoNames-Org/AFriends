package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;
import dev.no.name.afriends.core.MessageImage;
import dev.no.name.afriends.net.chat.ChatPayload;
import dev.no.name.afriends.net.media.PreparedImage;

import java.util.concurrent.CompletableFuture;

/**
 * Транспорт сети для шлюзов ({@link FriendsGateway}, {@link DirectGateway} и др.). Всё выполняется на потоке
 * сети при открытой связи; без связи ответ — код «offline». Строковый результат — «ok» или код отказа
 */
interface LinkCalls {
    /** Действие на сервере; после него список друзей и групп сверяется заново. */
    CompletableFuture<String> action(String op, JsonObject data);

    /** Запрос с ответом; null — связи нет или сервер отказал. */
    CompletableFuture<JsonObject> query(String op, JsonObject data);

    /** Запрос без сверки списка после него. */
    CompletableFuture<String> request(String op, JsonObject data);

    /** Сквозное сообщение другу, собеседнику или участнику общей группы. */
    CompletableFuture<String> send(String peerId, ChatPayload payload);

    /** Каждому участнику группы отдельным сквозным конвертом; «ok» — дошло до всех. */
    CompletableFuture<String> sendToGroup(String groupId, ChatPayload payload);

    /** Зашифровать своим ключом и залить; готово — описание картинки с ключом. */
    CompletableFuture<MessageImage> upload(PreparedImage prepared);

    /** Открытая картинка в чат AFriends вместе с подписью. */
    CompletableFuture<MessageImage> sendOfficialImage(PreparedImage prepared, String caption);

    void requestSync();
}
