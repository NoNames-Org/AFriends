package dev.no.name.blooddesigner.net.e2e;

import com.google.gson.JsonObject;
import dev.no.name.blooddesigner.net.crypto.Bytes;

// Закрепление ключей друзей: ключи запоминаются при первом знакомстве, и дальше любая смена — тревога.
// Так сервер не может незаметно подсунуть другу чужие ключи. Заодно помним, умел ли друг постквантовую
// защиту: если умел, а потом «разучился» — это попытка ослабить шифрование, а не поломка
public final class PinnedKeys {
    public enum Verdict { FIRST_SEEN, SAME, CHANGED, DOWNGRADED }

    private static final String RECORD = "pinned";

    private final SessionStore store;
    private final JsonObject pins;

    public PinnedKeys(SessionStore store) {
        this.store = store;
        JsonObject saved = store.load(RECORD);
        this.pins = saved == null ? new JsonObject() : saved;
    }

    // при FIRST_SEEN ключи закрепляются сразу; при CHANGED и DOWNGRADED остаются прежние, пока игрок не решит
    public Verdict check(FriendKeys keys, boolean hasPostQuantum) {
        JsonObject pin = pins.getAsJsonObject(keys.id());
        if (pin == null) {
            pin(keys, hasPostQuantum);
            return Verdict.FIRST_SEEN;
        }
        if (!pin.get("sign").getAsString().equals(Bytes.base64(keys.signKey()))
                || !pin.get("exchange").getAsString().equals(Bytes.base64(keys.exchangeKey()))) {
            return Verdict.CHANGED;
        }
        boolean pinnedPostQuantum = pin.get("pq").getAsBoolean();
        if (pinnedPostQuantum && !hasPostQuantum) {
            return Verdict.DOWNGRADED;
        }
        if (hasPostQuantum && !pinnedPostQuantum) {
            pin(keys, true);
        }
        return Verdict.SAME;
    }

    // для входящих: ключи друга уже закреплены и не совпадают. Незнакомые ключи не закрепляет —
    // это делает только отправка, где известно, умеет ли друг постквантовую защиту
    public boolean changed(FriendKeys keys) {
        JsonObject pin = pins.getAsJsonObject(keys.id());
        return pin != null && (!pin.get("sign").getAsString().equals(Bytes.base64(keys.signKey()))
                || !pin.get("exchange").getAsString().equals(Bytes.base64(keys.exchangeKey())));
    }

    // игрок сверил отпечаток и согласился на новые ключи
    public void accept(FriendKeys keys, boolean hasPostQuantum) {
        pin(keys, hasPostQuantum);
    }

    public void forget(String friendId) {
        pins.remove(friendId);
        store.save(RECORD, pins);
    }

    private void pin(FriendKeys keys, boolean hasPostQuantum) {
        JsonObject pin = new JsonObject();
        pin.addProperty("sign", Bytes.base64(keys.signKey()));
        pin.addProperty("exchange", Bytes.base64(keys.exchangeKey()));
        pin.addProperty("pq", hasPostQuantum);
        pins.add(keys.id(), pin);
        store.save(RECORD, pins);
    }
}
