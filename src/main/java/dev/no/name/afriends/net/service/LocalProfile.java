package dev.no.name.afriends.net.service;

import dev.no.name.afriends.net.model.Presence;

// что игрок показывает о себе: статус для друзей, цвет акцента, виден ли значок другим.
// playing — где игрок сейчас (адрес сервера или «меню»); это открытым текстом и только для портала
// админа, друзьям сервер показывается через зашифрованный presence. Собирается на потоке игры
public record LocalProfile(Presence presence, int accent, boolean visible, String playing) {
}
