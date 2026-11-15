package dev.no.name.blooddesigner.net.game;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Кто новый появился в табе. Значки спрашиваем у сервера только по этому событию, а не по таймеру
final class TabWatcher {
    private Set<String> seen = Set.of();

    // ники, которых в прошлый раз в табе не было; при смене сервера таб другой — все ники новые
    List<String> arrived(List<String> names) {
        Set<String> now = new HashSet<>();
        names.forEach(name -> now.add(name.toLowerCase(Locale.ROOT)));
        List<String> fresh = names.stream().filter(name -> !seen.contains(name.toLowerCase(Locale.ROOT))).toList();
        seen = now;
        return fresh;
    }

    // связь появилась заново: весь таб считается новым, вдруг что-то пропустили, пока её не было
    void forget() {
        seen = Set.of();
    }
}
