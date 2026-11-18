package dev.no.name.afriends.net.service;

// кто играет: ник из лаунчера и как доказать, что ник наш
public record PlayerAccount(String name, NameProof proof) {
}
