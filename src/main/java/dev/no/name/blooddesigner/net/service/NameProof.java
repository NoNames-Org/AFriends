package dev.no.name.blooddesigner.net.service;

// Подтверждение ника через Mojang: как при входе на лицензионный сервер, только serverId — challenge
// от сети. Зовётся на отдельном потоке, может блокировать. Бросает, если Mojang отказал
@FunctionalInterface
public interface NameProof {
    void join(String challenge) throws Exception;
}
