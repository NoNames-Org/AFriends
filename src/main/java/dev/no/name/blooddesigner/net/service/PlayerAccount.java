package dev.no.name.blooddesigner.net.service;

import dev.no.name.blooddesigner.net.model.ClientKind;

// кто играет: ник из лаунчера, каким модом представляться и как доказать, что ник наш
public record PlayerAccount(String name, ClientKind client, NameProof proof) {
}
