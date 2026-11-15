package dev.no.name.blooddesigner.api;

// Точка входа для модов-дополнений. В fabric.mod.json дополнения:
//   "entrypoints": { "blooddesigner": ["полное.имя.КлассаДополнения"] }
// BloodDesigner вызывает register один раз при запуске. Если BloodDesigner не стоит, класс дополнения никто не грузит
public interface BloodDesignerAddon {
    void register(BloodDesignerApi api);
}
