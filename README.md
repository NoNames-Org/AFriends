# AFriends

Мод для Fabric, который добавляет в Minecraft нормальный список друзей и мессенджер. Работает на любом сервере: зашёл на новый сервер, а друзья и переписки остались с тобой.

Раньше мод назывался BloodDesigner. Если он у тебя стоял, ничего переносить руками не нужно: при первом запуске AFriends сам заберёт настройки, переписки и ключи.

## Что умеет

**Друзья.** Добавляешь человека по нику и видишь, в сети ли он и когда заходил. Друга можно удалить или заглушить, а надоедливого игрока добавить в игнор.

**Мессенджер.** Личные чаты и группы, как в Telegram. Можно отправлять картинки, гифки и голосовые, отвечать на сообщения, закреплять их, делать опросы. Длинное сообщение пишется в несколько строк через Shift+Enter. Есть «Избранное» для своих заметок, архив и отметки «доставлено» и «прочитано».

**Группы.** Название, аватарка, описание и участники. Владелец раздаёт права: кому можно писать, кому отправлять гифки или голосовые, кому менять название. Ещё можно выдать участникам префиксы.

**Шифрование.** Сообщения между друзьями шифруются на твоём компьютере и расшифровываются только у получателя. Сервер их передаёт, но прочитать не может.

**Уведомления.** Новое сообщение всплывает в правом верхнем углу, даже когда мессенджер закрыт.

**Настройки.** Темы оформления, звук, выбор микрофона для голосовых. Там же видно, какая версия у тебя стоит и не вышла ли новая.

Все кнопки живут справа от чата, отдельные клавиши запоминать не надо.

## Свои аддоны

AFriends можно расширять. Аддон это обычный Fabric-мод, который добавляет свои кнопки у чата, пункты в меню друга, карточки в переписке или раздел в настройках.

В `fabric.mod.json` аддона:

```json
"entrypoints": { "afriends": ["com.example.MyAddon"] },
"depends": { "afriends": ">=2.0.0" }
```

Класс реализует `AFriendsAddon`. При запуске AFriends один раз вызывает у него `register(api)`, а дальше аддон сам решает, что добавить. Живой пример аддона: [BloodDesignerAdvance](https://github.com/NoNames-Org/BloodDesignerAdvance).

Чтобы собрать аддон против своей сборки AFriends, опубликуй её локально:

```
./gradlew publishToMavenLocal --no-parallel
```

и подключи в аддоне `dev.no.name:afriends:<версия>+<версия minecraft>` из `mavenLocal()`.

## Сборка из исходников

Нужен JDK 25.

```
./gradlew build dist
```

Готовые jar для всех версий появятся в папке `dist/`. Общий код лежит в `src/main/java`, а то, что отличается между версиями Minecraft, вынесено в `versions/<версия>`.

## Лицензия

MIT. Шрифт Inter распространяется по SIL Open Font License (`LICENSE_Inter_OFL.txt`).

---

# AFriends (English)

A Fabric mod that adds a proper friends list and messenger to Minecraft. It works on any server: hop onto a new server and your friends and chats come with you.

The mod used to be called BloodDesigner. If you had it installed, there's nothing to move by hand: on first launch AFriends picks up your settings, chats and keys on its own.

## What it does

**Friends.** Add someone by nickname and see whether they're online and when they were last around. You can remove or mute a friend, and put an annoying player on your ignore list.

**Messenger.** Direct chats and groups, Telegram-style. Send images, GIFs and voice messages, reply to messages, pin them, run polls. Long messages can span several lines with Shift+Enter. There's "Saved messages" for your own notes, an archive, and "delivered" and "read" marks.

**Groups.** A name, an avatar, a description and members. The owner hands out permissions: who can write, who can send GIFs or voice messages, who can rename the group. Members can also get prefixes.

**Encryption.** Messages between friends are encrypted on your computer and decrypted only on the recipient's. The server passes them along but can't read them.

**Notifications.** A new message pops up in the top-right corner, even when the messenger is closed.

**Settings.** Themes, sound, and picking a microphone for voice messages. This is also where you can see which version you have and whether a new one is out.

All the buttons live to the right of the chat, so there are no extra keybinds to remember.

## Your own addons

AFriends can be extended. An addon is a regular Fabric mod that adds its own buttons next to the chat, items in the friend menu, cards in conversations or a section in the settings.

In the addon's `fabric.mod.json`:

```json
"entrypoints": { "afriends": ["com.example.MyAddon"] },
"depends": { "afriends": ">=2.0.0" }
```

The class implements `AFriendsAddon`. On startup AFriends calls its `register(api)` once, and from there the addon decides what to add. A working example: [BloodDesignerAdvance](https://github.com/NoNames-Org/BloodDesignerAdvance).

To build an addon against your own AFriends build, publish it locally:

```
./gradlew publishToMavenLocal --no-parallel
```

and depend on `dev.no.name:afriends:<version>+<minecraft version>` from `mavenLocal()` in the addon.

## Building from source

You need JDK 25.

```
./gradlew build dist
```

Jars for every version end up in `dist/`. Shared code lives in `src/main/java`, and whatever differs between Minecraft versions is in `versions/<version>`.

## License

MIT. The Inter font is distributed under the SIL Open Font License (`LICENSE_Inter_OFL.txt`).
