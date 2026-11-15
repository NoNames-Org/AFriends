package dev.no.name.blooddesigner.net.game;

import dev.no.name.blooddesigner.BloodDesigner;
import dev.no.name.blooddesigner.net.badges.Badge;
import dev.no.name.blooddesigner.net.badges.BadgeDirectory;
import dev.no.name.blooddesigner.net.model.ClientKind;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

// Значок перед ником в табе и над головой: S — у игрока SMPHelper, D — у игрока с одним BloodDesigner,
// у подтверждённых через Mojang ещё зелёная галочка. Глифы — картинки из шрифта blooddesigner:badge
public final class BadgeText {
    private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(BloodDesigner.MOD_ID, "badge"));
    private static final String SMPHELPER = "";
    private static final String BLOODDESIGNER = "";
    private static final String VERIFIED = "";
    // белый цвет текста оставляет картинки глифов как есть
    private static final TextColor WHITE = TextColor.fromRgb(0xFFFFFF);

    // миксины зовутся из кода игры, поэтому справочник лежит в статике; до запуска сети значков нет
    private static volatile BadgeDirectory directory;
    private static volatile BooleanSupplier enabled = () -> false;

    private BadgeText() {
    }

    static void install(BadgeDirectory badges, BooleanSupplier active) {
        directory = badges;
        enabled = active;
    }

    // текст с ником игрока: со значком впереди, если игрок в сети мода
    public static Component prefixed(String name, Component text) {
        BadgeDirectory badges = directory;
        if (badges == null || text == null || !enabled.getAsBoolean()) {
            return text;
        }
        Badge badge = badges.badge(name);
        return badge == null ? text : Component.empty().append(of(badge)).append(text);
    }

    private static MutableComponent of(Badge badge) {
        Style base = Style.EMPTY.withFont(FONT).withColor(WHITE).withHoverEvent(new HoverEvent.ShowText(tooltip(badge)));
        MutableComponent text = Component.empty()
                .append(Component.literal(badge.client() == ClientKind.SMPHELPER ? SMPHELPER : BLOODDESIGNER).withStyle(base));
        if (badge.verified()) {
            text.append(Component.literal(VERIFIED).withStyle(base));
        }
        // пробел обычным шрифтом: в шрифте значка его нет, и он рисовался бы пустым квадратом
        return text.append(Component.literal(" "));
    }

    private static Component tooltip(Badge badge) {
        String mod = badge.client() == ClientKind.SMPHELPER ? "SMPHelper" : "BloodDesigner";
        return Component.literal(badge.verified() ? mod + " · ник подтверждён" : mod);
    }
}
