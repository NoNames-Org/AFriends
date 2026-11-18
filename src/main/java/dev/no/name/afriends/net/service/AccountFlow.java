package dev.no.name.afriends.net.service;

import com.google.gson.JsonObject;
import dev.no.name.afriends.AFriends;
import dev.no.name.afriends.net.crypto.Bytes;
import dev.no.name.afriends.net.identity.Identity;
import dev.no.name.afriends.net.rpc.Failures;
import dev.no.name.afriends.net.rpc.LinkCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

// После входа в туннель: занять ник и выложить ключ обмена, при случае подтвердить ник через Mojang.
// Поток сети
final class AccountFlow {
    // каким модом представляемся серверу сети
    private static final String CLIENT = "afriends";
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    enum Outcome { REGISTERED, NAME_TAKEN }

    private final LinkCalls calls;
    private final Identity identity;
    private final PlayerAccount account;
    private final Executor io;
    // Mojang спрашиваем один раз за запуск: офлайн-аккаунт всё равно не подтвердится
    private boolean verifyTried;

    AccountFlow(LinkCalls calls, Identity identity, PlayerAccount account, Executor io) {
        this.calls = calls;
        this.identity = identity;
        this.account = account;
        this.io = io;
    }

    CompletableFuture<Outcome> register() {
        JsonObject data = new JsonObject();
        data.addProperty("name", account.name());
        data.addProperty("boxKey", Bytes.base64(identity.exchangePublic()));
        data.addProperty("keySig", Bytes.base64(identity.keyCardSignature()));
        data.addProperty("client", CLIENT);
        return calls.request("register", data).handle((json, error) -> {
            if (error == null) {
                if (!json.get("verified").getAsBoolean()) {
                    verify();
                }
                return Outcome.REGISTERED;
            }
            if ("name_taken".equals(Failures.codeOf(error))) {
                return Outcome.NAME_TAKEN;
            }
            throw new CompletionException(error);
        });
    }

    // как вход на лицензионный сервер: Mojang подтверждает, что ник принадлежит этому игроку
    private void verify() {
        if (verifyTried) {
            return;
        }
        verifyTried = true;
        calls.request("verify.start", new JsonObject())
                .thenApplyAsync(json -> {
                    String challenge = json.get("challenge").getAsString();
                    try {
                        account.proof().join(challenge);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                    return challenge;
                }, io)
                .thenCompose(challenge -> {
                    JsonObject data = new JsonObject();
                    data.addProperty("challenge", challenge);
                    return calls.request("verify.finish", data);
                })
                .whenComplete((json, error) -> {
                    if (error != null) {
                        LOGGER.info("{}: ник не подтверждён через Mojang ({})", AFriends.NAME, Failures.codeOf(error));
                    } else if (json.get("verified").getAsBoolean()) {
                        LOGGER.info("{}: ник подтверждён через Mojang", AFriends.NAME);
                    }
                });
    }
}
