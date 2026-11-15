package dev.no.name.blooddesigner.net.model;

// какой мод у игрока сети. SMPHelper встраивает BloodDesigner и важнее: у его игроков значок S, у остальных D
public enum ClientKind {
    SMPHELPER("smphelper"),
    BLOODDESIGNER("blooddesigner");

    private final String wire;

    ClientKind(String wire) {
        this.wire = wire;
    }

    // как поле client пишется в запросах и ответах сервера
    public String wire() {
        return wire;
    }

    // незнакомое значение — BloodDesigner: его есть у всех игроков сети
    public static ClientKind fromWire(String value) {
        return SMPHELPER.wire.equals(value) ? SMPHELPER : BLOODDESIGNER;
    }
}
