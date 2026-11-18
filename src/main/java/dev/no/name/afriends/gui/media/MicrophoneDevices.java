package dev.no.name.afriends.gui.media;

import dev.no.name.afriends.AFriends;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Список микрофонов (устройств захвата OpenAL) для выбора в настройках звука. */
public final class MicrophoneDevices {
    private static final Logger LOGGER = LoggerFactory.getLogger(AFriends.NAME);

    private MicrophoneDevices() {
    }

    /** Имена доступных микрофонов; пустой список — перечисление не поддерживается, останется «По умолчанию». */
    public static List<String> list() {
        try {
            List<String> devices = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            return devices == null ? List.of() : List.copyOf(new ArrayList<>(devices));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            LOGGER.debug("{}: список микрофонов недоступен", AFriends.NAME, e);
            return List.of();
        }
    }
}
