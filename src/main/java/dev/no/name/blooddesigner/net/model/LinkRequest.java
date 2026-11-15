package dev.no.name.blooddesigner.net.model;

// заявка в друзья: входящая (нам) или исходящая (мы отправили)
public record LinkRequest(String id, String name, boolean verified, long created) {
}
