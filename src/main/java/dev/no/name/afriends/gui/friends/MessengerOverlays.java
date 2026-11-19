package dev.no.name.afriends.gui.friends;

import com.mojang.blaze3d.platform.InputConstants;
import dev.no.name.afriends.gui.sound.UiSound;
import dev.no.name.afriends.gui.sound.UiSounds;
import dev.no.name.afriends.gui.widget.ConfirmBox;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Кто первым получает событие в окне мессенджера. Порядок тот же, что был в длинных цепочках if у окна:
 * подтверждение и просмотр картинки, потом редакторы, настройки, пикеры, вложение и меню.
 * Что не забрал никто — достаётся шапке, ленте, рейке и списку
 */
final class MessengerOverlays {
    private MessengerOverlays() {
    }

    static OverlayStack build(MessengerPopups popups, ComposerView composer, Selection selection, RailView rail) {
        return new OverlayStack(clicks(popups, composer, selection, rail), keys(popups, composer, selection),
                typing(popups, composer), scrolls(popups));
    }

    // ---- клики ----

    private static List<Overlay> clicks(MessengerPopups popups, ComposerView composer, Selection selection, RailView rail) {
        ConfirmBox confirm = popups.confirmBox();
        ImageViewer viewer = popups.viewer();
        return List.of(
                new Overlay() {
                    @Override
                    public Result click(double x, double y, boolean right) {
                        if (!confirm.isOpen()) {
                            return Result.PASS;
                        }
                        confirm.click(x, y);
                        UiSounds.play(UiSound.CLICK);
                        return Result.CONSUMED;
                    }
                },
                new Overlay() {
                    @Override
                    public Result click(double x, double y, boolean right) {
                        if (!viewer.isOpen()) {
                            return Result.PASS;
                        }
                        popups.clickViewer(viewer.click(x, y));
                        return Result.CONSUMED;
                    }
                },
                editorClick(popups, popups.groupEditor()::isOpen, (x, y) -> popups.groupEditor().click(x, y)),
                editorClick(popups, popups.groupCreator()::isOpen, (x, y) -> popups.groupCreator().click(x, y)),
                editorClick(popups, popups.profilePanel()::isOpen, (x, y) -> popups.profilePanel().click(x, y)),
                editorClick(popups, popups.pollComposer()::isOpen, (x, y) -> popups.pollComposer().click(x, y)),
                editorClick(popups, popups.settingsPanel()::isOpen, (x, y) -> popups.settingsPanel().click(x, y)),
                new Overlay() {
                    // кнопка настроек внизу рейки открывает их поверх пикеров и меню
                    @Override
                    public Result click(double x, double y, boolean right) {
                        if (right || !rail.settingsAt(x, y)) {
                            return Result.PASS;
                        }
                        popups.openSettings();
                        return Result.CONSUMED;
                    }
                },
                new Overlay() {
                    @Override
                    public Result click(double x, double y, boolean right) {
                        if (!popups.forwardPicker().isOpen()) {
                            return Result.PASS;
                        }
                        popups.clickForward(x, y, selection);
                        return Result.CONSUMED;
                    }
                },
                new Overlay() {
                    @Override
                    public Result click(double x, double y, boolean right) {
                        if (!popups.gifPicker().isOpen()) {
                            return Result.PASS;
                        }
                        popups.clickGifs(x, y, right);
                        return Result.CONSUMED;
                    }
                },
                soundClick((x, y, right) -> !right && composer.attachment().click(x, y)),
                soundClick((x, y, right) -> popups.messageMenu().isOpen() && popups.messageMenu().click(x, y)),
                soundClick((x, y, right) -> popups.menu().click(x, y)));
    }

    private interface Click {
        boolean click(double x, double y);
    }

    private interface SideClick {
        boolean click(double x, double y, boolean right);
    }

    // открытый редактор забирает клик; закрылся — поле сообщения снова получает ввод
    private static Overlay editorClick(MessengerPopups popups, BooleanSupplier open, Click click) {
        return new Overlay() {
            @Override
            public Result click(double x, double y, boolean right) {
                return open.getAsBoolean() ? Result.of(popups.afterEditor(click.click(x, y))) : Result.PASS;
            }
        };
    }

    // попали — щелчок и событие забрано; мимо — дальше
    private static Overlay soundClick(SideClick click) {
        return new Overlay() {
            @Override
            public Result click(double x, double y, boolean right) {
                if (!click.click(x, y, right)) {
                    return Result.PASS;
                }
                UiSounds.play(UiSound.CLICK);
                return Result.CONSUMED;
            }
        };
    }

    // ---- клавиши ----

    private static boolean isEnter(int key) {
        return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
    }

    private static List<Overlay> keys(MessengerPopups popups, ComposerView composer, Selection selection) {
        ConfirmBox confirm = popups.confirmBox();
        VoiceStrip voice = composer.voiceStrip();
        return List.of(
                new Overlay() {
                    @Override
                    public Result key(int key) {
                        if (!confirm.isOpen()) {
                            return Result.PASS;
                        }
                        if (isEnter(key)) {
                            confirm.confirm();
                        } else if (key == InputConstants.KEY_ESCAPE) {
                            confirm.close();
                        }
                        return Result.CONSUMED;
                    }
                },
                editorKey(popups, popups.groupEditor()::isOpen, popups.groupEditor()::keyPressed),
                editorKey(popups, popups.groupCreator()::isOpen, popups.groupCreator()::keyPressed),
                // поверх профиля открыта картинка — клавиши профилю не идут
                editorKey(popups, () -> popups.profilePanel().isOpen() && !popups.viewer().isOpen(),
                        popups.profilePanel()::keyPressed),
                editorKey(popups, popups.pollComposer()::isOpen, popups.pollComposer()::keyPressed),
                editorKey(popups, popups.settingsPanel()::isOpen, popups.settingsPanel()::key),
                new Overlay() {
                    @Override
                    public Result key(int key) {
                        if (!voice.isRecording()) {
                            return Result.PASS;
                        }
                        if (key == InputConstants.KEY_ESCAPE) {
                            voice.cancel();
                        } else if (isEnter(key)) {
                            voice.finish();
                        }
                        return Result.CONSUMED;
                    }
                },
                escape(() -> popups.menu().isOpen() || popups.messageMenu().isOpen(), () -> {
                    popups.menu().close();
                    popups.messageMenu().close();
                }),
                escape(() -> popups.viewer().isOpen() || popups.gifPicker().isOpen() || popups.forwardPicker().isOpen(), () -> {
                    popups.viewer().close();
                    popups.gifPicker().close();
                    popups.forwardPicker().close();
                }),
                // Esc снимает выделение, потом отменяет ответ, и только потом закрывает окно
                escape(selection::isActive, selection::clear),
                escape(() -> composer.replyStrip().isActive(), () -> composer.replyStrip().clear()));
    }

    private static Overlay editorKey(MessengerPopups popups, BooleanSupplier open, IntPredicate key) {
        return new Overlay() {
            @Override
            public Result key(int pressed) {
                return open.getAsBoolean() ? Result.of(popups.afterEditor(key.test(pressed))) : Result.PASS;
            }
        };
    }

    private static Overlay escape(BooleanSupplier active, Runnable close) {
        return new Overlay() {
            @Override
            public Result key(int key) {
                if (key != InputConstants.KEY_ESCAPE || !active.getAsBoolean()) {
                    return Result.PASS;
                }
                close.run();
                return Result.CONSUMED;
            }
        };
    }

    // ---- ввод текста ----

    private static List<Overlay> typing(MessengerPopups popups, ComposerView composer) {
        return List.of(
                blockTyping(popups.confirmBox()::isOpen),
                editorTyping(popups.groupEditor()::isOpen, popups.groupEditor()::charTyped),
                editorTyping(popups.groupCreator()::isOpen, popups.groupCreator()::charTyped),
                editorTyping(popups.profilePanel()::isOpen, popups.profilePanel()::charTyped),
                editorTyping(popups.pollComposer()::isOpen, popups.pollComposer()::charTyped),
                new Overlay() {
                    @Override
                    public Result type(String text) {
                        if (!popups.settingsPanel().isOpen()) {
                            return Result.PASS;
                        }
                        popups.settingsPanel().type(text);
                        return Result.CONSUMED;
                    }
                },
                blockTyping(() -> composer.voiceStrip().isRecording()));
    }

    private static Overlay editorTyping(BooleanSupplier open, Function<String, Boolean> typed) {
        return new Overlay() {
            @Override
            public Result type(String text) {
                return open.getAsBoolean() ? Result.of(typed.apply(text)) : Result.PASS;
            }
        };
    }

    // окошко открыто — ввод никуда не идёт, но считается обработанным
    private static Overlay blockTyping(BooleanSupplier open) {
        return new Overlay() {
            @Override
            public Result type(String text) {
                return open.getAsBoolean() ? Result.CONSUMED : Result.PASS;
            }
        };
    }

    // ---- колесо ----

    private interface Scroll {
        boolean scroll(double x, double y, double amount);
    }

    private static List<Overlay> scrolls(MessengerPopups popups) {
        return List.of(
                scroll(popups.gifPicker()::scroll),
                scroll(popups.forwardPicker()::scroll),
                scroll(popups.groupEditor()::scroll),
                scroll(popups.groupCreator()::scroll),
                scroll((x, y, amount) -> !popups.viewer().isOpen() && popups.profilePanel().scroll(x, y, amount)));
    }

    private static Overlay scroll(Scroll scroll) {
        return new Overlay() {
            @Override
            public Result scroll(double x, double y, double amount) {
                return Result.consumedIf(scroll.scroll(x, y, amount));
            }
        };
    }
}
