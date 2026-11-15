package dev.no.name.blooddesigner.net.service;

import dev.no.name.blooddesigner.net.model.Presence;

// что игрок показывает о себе: статус для друзей, цвет акцента, виден ли значок другим.
// Собирается на потоке игры и целиком передаётся сети
public record LocalProfile(Presence presence, int accent, boolean visible) {
}
