package dev.no.name.afriends.api;

// свой статус под ником в шапке друзей; по клику его можно изменить
public interface ProfileStatus {
    // false — шапка как обычно
    boolean active();

    String get();

    void set(String status);
}
