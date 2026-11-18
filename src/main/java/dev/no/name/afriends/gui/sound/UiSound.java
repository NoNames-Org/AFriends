package dev.no.name.afriends.gui.sound;

import dev.no.name.afriends.AFriends;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public enum UiSound {
    CLICK("click", 0.45f),
    DROPDOWN_OPEN("dropdown_open", 0.42f),
    DROPDOWN_CLOSE("dropdown_close", 0.35f),
    SELECT("select", 0.45f),
    TYPE("type", 0.3f),
    DELETE("delete", 0.3f),
    MENU_OPEN("menu_open", 0.45f),
    // входящее сообщение (sounds/notify/message.ogg, свой синтез)
    MESSAGE("message", 0.7f);

    private final SoundEvent event;
    private final float volume;

    UiSound(String name, float volume) {
        this.event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(AFriends.MOD_ID, "ui." + name));
        this.volume = volume;
    }

    public SoundEvent getEvent() {
        return event;
    }

    public float getVolume() {
        return volume;
    }
}
