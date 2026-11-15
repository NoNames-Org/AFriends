package dev.no.name.blooddesigner.net.badges;

import dev.no.name.blooddesigner.net.model.ClientKind;

// значок у ника: S или D по моду игрока, галочка — ник подтверждён через Mojang
public record Badge(ClientKind client, boolean verified) {
}
