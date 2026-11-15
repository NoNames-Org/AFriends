package dev.no.name.blooddesigner.gui.battlepass;

import dev.no.name.blooddesigner.compat.Gfx;
import dev.no.name.blooddesigner.core.battlepass.BattlePassMenu;
import dev.no.name.blooddesigner.core.battlepass.BattlePassMenu.Reward;
import dev.no.name.blooddesigner.core.battlepass.BattlePassMenu.View;
import dev.no.name.blooddesigner.core.battlepass.BattlePassNavigator;
import dev.no.name.blooddesigner.core.battlepass.BattlePassProgress;
import dev.no.name.blooddesigner.core.battlepass.BloodBalance;
import dev.no.name.blooddesigner.core.battlepass.QuestMenu;
import dev.no.name.blooddesigner.core.battlepass.QuestText.Quest;
import dev.no.name.blooddesigner.gui.Theme;
import dev.no.name.blooddesigner.gui.animation.Easing;
import dev.no.name.blooddesigner.gui.animation.Motion;
import dev.no.name.blooddesigner.gui.battlepass.MenuUi.ButtonStyle;
import dev.no.name.blooddesigner.gui.render.Colors;
import dev.no.name.blooddesigner.gui.render.Draw;
import dev.no.name.blooddesigner.gui.render.icon.Icon;
import dev.no.name.blooddesigner.gui.render.text.TextStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Свой экран уровней БП: баннер с Premium, панель заданий, полоса уровня, две линии наград, страницы. */
public class BattlePassScreen extends MenuScreenBase {
    private static final int WIDTH = 538;
    private static final int GAP = 8;
    private static final int TOP_HEIGHT = 146;
    private static final int QUEST_PANEL_WIDTH = 186;
    private static final int BANNER_WIDTH = WIDTH - QUEST_PANEL_WIDTH - GAP;
    private static final int LEVEL_HEIGHT = 28;
    private static final int LABEL_WIDTH = 25;
    private static final int COLUMNS = BattlePassMenu.LEVELS_PER_PAGE;
    private static final int CELL_WIDTH = (WIDTH - LABEL_WIDTH) / COLUMNS;
    private static final int ROW_HEIGHT = 74;
    private static final int PAGER_HEIGHT = 22;
    private static final int HEIGHT = TOP_HEIGHT + GAP + LEVEL_HEIGHT + GAP + ROW_HEIGHT * 2 + GAP + PAGER_HEIGHT;
    private static final int PAD = 12;
    private static final int PAGE_BUTTONS = 9;
    private static final int PAGE_BUTTON = 22;
    private static final int PAGE_GAP = 5;
    private static final long PULSE_PERIOD = 1400;
    private static final long PAGE_STEP_TIMEOUT_MS = 1500;
    private static final int PREMIUM_CARD_HEIGHT = 50;

    /**
     * Цель листания живёт вне экрана: при переходе на страницу сервер может закрыть меню и открыть новое,
     * и тогда наш экран создаётся заново. Без этого переход «1 → 3» останавливался на 2.
     */
    private static int targetPage;
    private static int steppedFrom = -1;
    private static long steppedAt;
    private static long targetSetAt;
    private static final long TARGET_LIFETIME_MS = 15_000;

    private Component serverTitle;

    public BattlePassScreen(Component serverTitle) {
        super("Батл пасс");
        this.serverTitle = serverTitle;
        if (Motion.now() - targetSetAt > TARGET_LIFETIME_MS) {
            targetPage = 0;
        }
    }

    /** Сервер открыл следующую страницу: остаёмся на этом экране и просто берём новый заголовок. */
    public void follow(Component title) {
        serverTitle = title;
    }

    @Override
    protected int canvasWidth() {
        return WIDTH;
    }

    @Override
    protected int canvasHeight() {
        return HEIGHT;
    }

    private View view() {
        AbstractContainerMenu menu = menu();
        return menu == null ? null : BattlePassMenu.read(menu, serverTitle);
    }

    // ---------- раскладка ----------

    private int levelTop() {
        return top + TOP_HEIGHT + GAP;
    }

    private int gridTop() {
        return levelTop() + LEVEL_HEIGHT + GAP;
    }

    private int rowTop(boolean premium) {
        return gridTop() + (premium ? ROW_HEIGHT : 0);
    }

    private int cellX(int column) {
        return left + LABEL_WIDTH + column * CELL_WIDTH;
    }

    private int pagerTop() {
        return gridTop() + ROW_HEIGHT * 2 + GAP;
    }

    private int questX() {
        return left + BANNER_WIDTH + GAP;
    }

    private int premiumCardY() {
        return top + TOP_HEIGHT - PAD - PREMIUM_CARD_HEIGHT;
    }

    private int buyX() {
        return left + PAD + 236 - 8 - 64;
    }

    private int buyY() {
        return premiumCardY() + (PREMIUM_CARD_HEIGHT - MenuUi.BUTTON_HEIGHT) / 2;
    }

    private int allQuestsY() {
        return top + TOP_HEIGHT - PAD - MenuUi.BUTTON_HEIGHT;
    }

    private int closeX() {
        return left + BANNER_WIDTH - PAD - MenuUi.BUTTON_HEIGHT;
    }

    // ---------- отрисовка ----------

    @Override
    protected void drawCanvas(Gfx g, int mouseX, int mouseY) {
        View view = view();
        if (view == null) {
            return;
        }
        stepTowardsTarget(view);
        drawBanner(g, view, mouseX, mouseY);
        drawQuestPanel(g, mouseX, mouseY);
        drawLevel(g, view.progress());
        drawGrid(g, view, mouseX, mouseY);
        drawPager(g, view, mouseX, mouseY);
        g.nextStratum();
        drawItems(g, view);
        g.nextStratum();
        drawCellBadges(g, view);
    }

    private void drawBanner(Gfx g, View view, int mouseX, int mouseY) {
        MenuUi.banner(g, left, top, BANNER_WIDTH, TOP_HEIGHT, Icon.TROPHY);
        MenuUi.headline(g, "БАТЛ ПАСС", left + PAD + 2, top + PAD + 2, 2.6f, Theme.TEXT);
        BattlePassProgress progress = view.progress();
        Draw.text(g, "Твой уровень", left + PAD + 3, top + 50, Colors.fade(Theme.TEXT, 0.75f));
        String level = String.valueOf(progress.level());
        MenuUi.headline(g, level, left + PAD + 2, top + 61, 1.8f, Theme.TEXT);
        if (progress.maxLevel() > 0) {
            Draw.text(g, "/ " + progress.maxLevel(), left + PAD + 8 + MenuUi.headlineWidth(level, 1.8f), top + 68,
                    Colors.fade(Theme.TEXT, 0.6f));
        }
        boolean closeHovered = inside(mouseX, mouseY, closeX(), top + PAD, MenuUi.BUTTON_HEIGHT, MenuUi.BUTTON_HEIGHT);
        MenuUi.button(g, closeX(), top + PAD, MenuUi.BUTTON_HEIGHT, Icon.CLOSE, null, ButtonStyle.OUTLINE, true, closeHovered);
        MenuUi.balance(g, closeX() - 6, top + PAD, BloodBalance.current());
        drawPremiumCard(g, progress, mouseX, mouseY);
    }

    private void drawPremiumCard(Gfx g, BattlePassProgress progress, int mouseX, int mouseY) {
        int x = left + PAD;
        int y = premiumCardY();
        int w = 236;
        Draw.box(g, x, y, w, PREMIUM_CARD_HEIGHT, Theme.RADIUS + 2, 0x66000000, Colors.fade(MenuUi.GOLD, 0.55f));
        Draw.icon(g, Icon.CROWN, x + 8, y + 8, Theme.ICON_SMALL, MenuUi.GOLD);
        Draw.text(g, "PREMIUM", x + 20, y + 8, MenuUi.GOLD, TextStyle.BODY_MEDIUM);
        if (progress.premiumActive()) {
            Draw.text(g, "Активен  ·  уровень " + progress.premiumLevel(), x + 8, y + 22, Theme.TEXT);
            MenuUi.progressBar(g, x + 8, y + 36, w - 16, 5, progress.premiumFill(), MenuUi.GOLD);
            return;
        }
        Draw.text(g, "Своя линия наград", x + 8, y + 22, Colors.fade(Theme.TEXT, 0.85f));
        Draw.text(g, "и косметика на уровнях", x + 8, y + 33, Colors.fade(Theme.TEXT, 0.85f));
        boolean hovered = inside(mouseX, mouseY, buyX(), buyY(), 64, MenuUi.BUTTON_HEIGHT);
        MenuUi.button(g, buyX(), buyY(), 64, Icon.CROWN, "Купить", ButtonStyle.LIGHT, true, hovered);
    }

    private void drawQuestPanel(Gfx g, int mouseX, int mouseY) {
        int x = questX();
        MenuUi.panel(g, x, top, QUEST_PANEL_WIDTH, TOP_HEIGHT);
        Draw.icon(g, Icon.BOOK, x + PAD, top + PAD + 1, Theme.ICON_SMALL, Theme.ACCENT);
        Draw.text(g, "Задания", x + PAD + 13, top + PAD, Theme.TEXT, TextStyle.BODY_MEDIUM);
        List<Quest> quests = previewQuests();
        int w = QUEST_PANEL_WIDTH - PAD * 2;
        if (quests.isEmpty()) {
            int hintY = top + 32;
            for (String line : MenuUi.wrap("Открой все задания, и здесь появится твой прогресс.", w, 3, TextStyle.BODY)) {
                Draw.text(g, line, x + PAD, hintY, Theme.TEXT_DIM, TextStyle.BODY);
                hintY += 11;
            }
        }
        int y = top + 30;
        for (Quest quest : quests) {
            Draw.text(g, Draw.trim(quest.title(), w - 40), x + PAD, y, quest.done() ? MenuUi.GREEN : Theme.TEXT);
            String count = quest.progress() + "/" + quest.goal();
            Draw.text(g, count, x + PAD + w - Draw.width(count), y, Theme.TEXT_SOFT);
            MenuUi.progressBar(g, x + PAD, y + 12, w, 4, quest.fill(), quest.done() ? MenuUi.GREEN : Theme.ACCENT);
            Draw.text(g, quest.reward(), x + PAD, y + 20, MenuUi.GOLD, TextStyle.BODY);
            y += 36;
        }
        boolean hovered = inside(mouseX, mouseY, x + PAD, allQuestsY(), w, MenuUi.BUTTON_HEIGHT);
        MenuUi.button(g, x + PAD, allQuestsY(), w, Icon.LIST, "Все задания", ButtonStyle.LIGHT, true, hovered);
    }

    /** Два невыполненных квеста из последнего открытия меню квестов (или два первых, если всё выполнено). */
    private static List<Quest> previewQuests() {
        List<Quest> all = QuestMenu.lastSeen();
        List<Quest> preview = new ArrayList<>();
        for (Quest quest : all) {
            if (!quest.done() && preview.size() < 2) {
                preview.add(quest);
            }
        }
        for (int i = 0; i < all.size() && preview.size() < 2; i++) {
            if (!preview.contains(all.get(i))) {
                preview.add(all.get(i));
            }
        }
        return preview;
    }

    private void drawLevel(Gfx g, BattlePassProgress progress) {
        int y = levelTop();
        MenuUi.panel(g, left, y, WIDTH, LEVEL_HEIGHT);
        int barX = left + 8;
        int barW = WIDTH - 16;
        int barH = 16;
        int barY = y + (LEVEL_HEIGHT - barH) / 2;
        Draw.roundedRect(g, barX, barY, barW, barH, Theme.RADIUS, 0x66000000);
        String label = "Уровень " + progress.level();
        float pill = Draw.width(label, TextStyle.BODY_MEDIUM) + 16;
        float fillW = Math.max(pill, pill + (barW - pill) * progress.fill());
        Draw.gradientHorizontal(g, barX, barY, fillW, barH, Theme.RADIUS,
                Colors.fade(Theme.ACCENT, 0.85f), Colors.fade(Theme.ACCENT, 0.45f));
        Draw.text(g, label, barX + 8, barY + (barH - 8) / 2f, Theme.ACCENT_TEXT, TextStyle.BODY_MEDIUM);
        String xp = "Опыт " + progress.xp() + " / " + progress.xpNeeded();
        Draw.text(g, xp, barX + barW - 8 - Draw.width(xp), barY + (barH - 8) / 2f, Theme.TEXT);
    }

    private void drawGrid(Gfx g, View view, int mouseX, int mouseY) {
        int y = gridTop();
        MenuUi.panel(g, left, y, WIDTH, ROW_HEIGHT * 2);
        Draw.roundedRect(g, left + 1, rowTop(true), WIDTH - 2, ROW_HEIGHT - 1, Theme.PANEL_RADIUS - 1,
                Colors.fade(MenuUi.GOLD, 0.09f));
        Draw.rect(g, left + 1, rowTop(true), WIDTH - 2, 1, Theme.BORDER_HOVER);
        Draw.rect(g, left + LABEL_WIDTH, y + 1, 1, ROW_HEIGHT * 2 - 2, Theme.BORDER_HOVER);
        MenuUi.verticalText(g, "Обычная", left + LABEL_WIDTH / 2f, rowTop(false) + ROW_HEIGHT / 2f, Theme.TEXT_SOFT,
                TextStyle.BODY_MEDIUM);
        MenuUi.verticalText(g, "Премиум", left + LABEL_WIDTH / 2f, rowTop(true) + ROW_HEIGHT / 2f, MenuUi.GOLD,
                TextStyle.BODY_MEDIUM);
        for (int column = 1; column < COLUMNS; column++) {
            for (boolean premium : new boolean[]{false, true}) {
                Draw.rect(g, cellX(column), rowTop(premium) + 6, 1, ROW_HEIGHT - 12, Colors.fade(Theme.BORDER_HOVER, 0.7f));
            }
        }
        drawRow(g, view.free(), false, view.progress(), mouseX, mouseY);
        drawRow(g, view.premium(), true, view.progress(), mouseX, mouseY);
        if (view.premiumEnded() && view.premium().isEmpty() && !view.free().isEmpty()) {
            String ended = "Premium-линия закончилась на 100 уровне";
            float w = Draw.width(ended) + 20;
            float cx = left + LABEL_WIDTH + (WIDTH - LABEL_WIDTH) / 2f;
            Draw.roundedRect(g, cx - w / 2f, rowTop(true) + ROW_HEIGHT / 2f - 9, w, 18, 9, 0x66000000);
            Draw.text(g, ended, cx - w / 2f + 10, rowTop(true) + ROW_HEIGHT / 2f - 4, MenuUi.GOLD);
        }
    }

    private void drawRow(Gfx g, List<Reward> rewards, boolean premium, BattlePassProgress progress, int mouseX, int mouseY) {
        int rowY = rowTop(premium);
        float pulse = Easing.pulse(Motion.now(), PULSE_PERIOD);
        for (int i = 0; i < rewards.size() && i < COLUMNS; i++) {
            Reward reward = rewards.get(i);
            int x = cellX(reward.column());
            boolean hovered = inside(mouseX, mouseY, x, rowY, CELL_WIDTH, ROW_HEIGHT);
            boolean available = isAvailable(reward, premium, progress);
            if (hovered) {
                Draw.roundedRect(g, x + 2, rowY + 2, CELL_WIDTH - 4, ROW_HEIGHT - 4, Theme.RADIUS, Colors.fade(Theme.TEXT, 0.05f));
            }
            if (available) {
                int glow = Colors.fade(Theme.ACCENT, 0.35f + 0.45f * pulse);
                Draw.border(g, x + 2, rowY + 2, CELL_WIDTH - 4, ROW_HEIGHT - 4, Theme.RADIUS, glow);
                float pillW = Draw.width("Забрать", TextStyle.BODY) + 10;
                Draw.roundedRect(g, x + (CELL_WIDTH - pillW) / 2f, rowY + ROW_HEIGHT - 17, pillW, 12, 6, Theme.ACCENT);
                Draw.text(g, "Забрать", x + (CELL_WIDTH - pillW) / 2f + 5, rowY + ROW_HEIGHT - 15, Theme.ACCENT_TEXT, TextStyle.BODY);
            }
            if (reward.level() > 0) {
                boolean current = !premium && reward.level() == progress.level();
                Draw.text(g, String.valueOf(reward.level()), x + 5, rowY + 5, current ? Theme.ACCENT : Theme.TEXT_SOFT,
                        TextStyle.BODY_MEDIUM);
            }
        }
    }

    private void drawItems(Gfx g, View view) {
        drawRowItems(g, view.free(), false);
        drawRowItems(g, view.premium(), true);
    }

    private void drawRowItems(Gfx g, List<Reward> rewards, boolean premium) {
        int rowY = rowTop(premium);
        float size = 16 * 2.25f;
        for (int i = 0; i < rewards.size() && i < COLUMNS; i++) {
            MenuUi.item(g, minecraft.font, rewards.get(i).stack(), cellX(rewards.get(i).column()) + (CELL_WIDTH - size) / 2f,
                    rowY + (ROW_HEIGHT - size) / 2f + 2, 2.25f);
        }
    }

    private void drawCellBadges(Gfx g, View view) {
        drawRowBadges(g, view.free(), false, view.progress());
        drawRowBadges(g, view.premium(), true, view.progress());
    }

    private void drawRowBadges(Gfx g, List<Reward> rewards, boolean premium, BattlePassProgress progress) {
        int rowY = rowTop(premium);
        for (int i = 0; i < rewards.size() && i < COLUMNS; i++) {
            Reward reward = rewards.get(i);
            int x = cellX(reward.column());
            float badgeX = x + CELL_WIDTH - Theme.ICON_SMALL - 5;
            if (reward.claimed()) {
                Draw.roundedRect(g, x + 2, rowY + 2, CELL_WIDTH - 4, ROW_HEIGHT - 4, Theme.RADIUS, 0x59000000);
                if (!reward.claimedIcon()) {
                    Draw.icon(g, Icon.CHECK, badgeX, rowY + 6, Theme.ICON_SMALL, MenuUi.GREEN);
                }
            } else if (isLocked(reward, premium, progress)) {
                Draw.roundedRect(g, x + 2, rowY + 2, CELL_WIDTH - 4, ROW_HEIGHT - 4, Theme.RADIUS, 0x38000000);
                Draw.icon(g, premium && !progress.premiumActive() ? Icon.CROWN : Icon.LOCK, badgeX, rowY + 6,
                        Theme.ICON_SMALL, premium ? MenuUi.GOLD : Theme.TEXT_DIM);
            }
        }
    }

    private void drawPager(Gfx g, View view, int mouseX, int mouseY) {
        int y = pagerTop();
        int[] pages = pageNumbers(view);
        float total = (pages.length + 2) * (PAGE_BUTTON + PAGE_GAP) - PAGE_GAP;
        float x = left + (WIDTH - total) / 2f;
        drawPageButton(g, x, y, Icon.CHEVRON_LEFT, null, false, view.hasPrev(), mouseX, mouseY);
        x += PAGE_BUTTON + PAGE_GAP;
        for (int page : pages) {
            boolean current = page == view.page();
            drawPageButton(g, x, y, null, page < 0 ? "…" : String.valueOf(page), current, page > 0, mouseX, mouseY);
            x += PAGE_BUTTON + PAGE_GAP;
        }
        drawPageButton(g, x, y, Icon.CHEVRON_RIGHT, null, false, view.hasNext(), mouseX, mouseY);
        if (targetPage > 0 && targetPage != view.page()) {
            String status = "Листаю к странице " + targetPage + "…";
            Draw.text(g, status, left + WIDTH - Draw.width(status, TextStyle.BODY), y + 8, Theme.TEXT_DIM, TextStyle.BODY);
        }
    }

    private void drawPageButton(Gfx g, float x, float y, Icon icon, String label, boolean current, boolean enabled,
                                int mouseX, int mouseY) {
        boolean hovered = enabled && !current && inside(mouseX, mouseY, x, y, PAGE_BUTTON, PAGE_BUTTON);
        int fill = current ? Theme.ACCENT : hovered ? Theme.CARD_HOVER : Colors.fade(Theme.CARD, 0.9f);
        int border = current ? Theme.ACCENT : hovered ? Theme.BORDER_SELECTED : Theme.BORDER_HOVER;
        int color = current ? Theme.ACCENT_TEXT : enabled ? (hovered ? Theme.TEXT : Theme.TEXT_SOFT) : Theme.TEXT_FAINT;
        Draw.box(g, x, y, PAGE_BUTTON, PAGE_BUTTON, PAGE_BUTTON / 2f, fill, border);
        if (icon != null) {
            Draw.icon(g, icon, x + (PAGE_BUTTON - Theme.ICON_SMALL) / 2f, y + (PAGE_BUTTON - Theme.ICON_SMALL) / 2f,
                    Theme.ICON_SMALL, color);
        } else {
            Draw.text(g, label, x + (PAGE_BUTTON - Draw.width(label)) / 2f, y + (PAGE_BUTTON - 8) / 2f, color);
        }
    }

    /** Номера страниц: первая, пять вокруг текущей, последняя; -1 — многоточие между ними. */
    private static int[] pageNumbers(View view) {
        int total = Math.max(view.totalPages(), view.page());
        if (total <= PAGE_BUTTONS) {
            return java.util.stream.IntStream.rangeClosed(1, total).toArray();
        }
        int from = Math.max(2, view.page() - 2);
        int to = Math.min(total - 1, view.page() + 2);
        while (to - from < 4) {
            if (from > 2) {
                from--;
            } else if (to < total - 1) {
                to++;
            } else {
                break;
            }
        }
        List<Integer> pages = new ArrayList<>();
        pages.add(1);
        if (from > 2) {
            pages.add(-1);
        }
        for (int page = from; page <= to; page++) {
            pages.add(page);
        }
        if (to < total - 1) {
            pages.add(-1);
        }
        pages.add(total);
        return pages.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Сервер листает только на одну страницу, поэтому к выбранной идём шагами. Новая страница первое
     * мгновение приходит пустой (без стрелок), поэтому шагаем только когда её содержимое уже пришло.
     */
    private void stepTowardsTarget(View view) {
        AbstractContainerMenu menu = menu();
        if (targetPage <= 0 || menu == null) {
            return;
        }
        if (targetPage == view.page()) {
            targetPage = 0;
            return;
        }
        long now = Motion.now();
        // после клика сервер сразу убирает стрелки в старом окне и лишь потом открывает новое:
        // пока мы в окне, где уже кликнули, просто ждём новое окно
        if (steppedFrom == menu.containerId) {
            if (now - steppedAt > PAGE_STEP_TIMEOUT_MS) {
                targetPage = 0;
            }
            return;
        }
        boolean loaded = view.hasNext() || view.hasPrev() || !view.free().isEmpty();
        if (!loaded) {
            if (steppedAt > 0 && now - steppedAt > PAGE_STEP_TIMEOUT_MS) {
                targetPage = 0;
            }
            return;
        }
        boolean forward = targetPage > view.page();
        if (forward ? !view.hasNext() : !view.hasPrev()) {
            targetPage = 0;
            return;
        }
        steppedFrom = menu.containerId;
        steppedAt = now;
        clickSlot(forward ? BattlePassMenu.NEXT_SLOT : BattlePassMenu.PREV_SLOT, false);
    }

    // ---------- состояние награды ----------

    private static boolean isAvailable(Reward reward, boolean premium, BattlePassProgress progress) {
        return !reward.claimed() && !isLocked(reward, premium, progress);
    }

    private static boolean isLocked(Reward reward, boolean premium, BattlePassProgress progress) {
        if (reward.level() <= 0) {
            return false;
        }
        // Premium качается отдельно с нуля, поэтому у него свой уровень
        int lineLevel = premium ? progress.premiumLevel() : progress.level();
        return reward.level() > lineLevel;
    }

    private Reward rewardAt(View view, double x, double y) {
        for (boolean premium : new boolean[]{false, true}) {
            List<Reward> rewards = premium ? view.premium() : view.free();
            for (int i = 0; i < rewards.size() && i < COLUMNS; i++) {
                if (inside(x, y, cellX(rewards.get(i).column()), rowTop(premium), CELL_WIDTH, ROW_HEIGHT)) {
                    return rewards.get(i);
                }
            }
        }
        return null;
    }

    // ---------- подсказки и ввод ----------

    @Override
    protected void drawTooltips(Gfx g, int canvasX, int canvasY, int screenX, int screenY) {
        View view = view();
        if (view == null) {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        Reward reward = rewardAt(view, canvasX, canvasY);
        if (reward != null) {
            hovered = reward.stack();
        } else if (inside(canvasX, canvasY, left, levelTop(), WIDTH, LEVEL_HEIGHT)) {
            hovered = view.progressItem();
        }
        if (!hovered.isEmpty()) {
            g.itemTooltip(minecraft.font, hovered, screenX, screenY);
        }
    }

    @Override
    protected boolean onCanvasClick(double x, double y) {
        View view = view();
        if (view == null) {
            return false;
        }
        if (inside(x, y, closeX(), top + PAD, MenuUi.BUTTON_HEIGHT, MenuUi.BUTTON_HEIGHT)) {
            onClose();
            return true;
        }
        if (!view.progress().premiumActive() && inside(x, y, buyX(), buyY(), 64, MenuUi.BUTTON_HEIGHT)) {
            BattlePassNavigator.openShop();
            return true;
        }
        if (inside(x, y, questX() + PAD, allQuestsY(), QUEST_PANEL_WIDTH - PAD * 2, MenuUi.BUTTON_HEIGHT)) {
            clickSlot(BattlePassMenu.QUESTS_SLOT, false);
            return true;
        }
        if (clickPager(view, x, y)) {
            return true;
        }
        Reward reward = rewardAt(view, x, y);
        if (reward != null) {
            clickSlot(reward.slot(), true);
            return true;
        }
        return false;
    }

    private boolean clickPager(View view, double x, double y) {
        int top = pagerTop();
        int[] pages = pageNumbers(view);
        float total = (pages.length + 2) * (PAGE_BUTTON + PAGE_GAP) - PAGE_GAP;
        float bx = left + (WIDTH - total) / 2f;
        if (inside(x, y, bx, top, PAGE_BUTTON, PAGE_BUTTON)) {
            goTo(view, view.page() - 1, view.hasPrev());
            return true;
        }
        bx += PAGE_BUTTON + PAGE_GAP;
        for (int page : pages) {
            if (page > 0 && inside(x, y, bx, top, PAGE_BUTTON, PAGE_BUTTON)) {
                goTo(view, page, page != view.page());
                return true;
            }
            bx += PAGE_BUTTON + PAGE_GAP;
        }
        if (inside(x, y, bx, top, PAGE_BUTTON, PAGE_BUTTON)) {
            goTo(view, view.page() + 1, view.hasNext());
            return true;
        }
        return false;
    }

    private void goTo(View view, int page, boolean allowed) {
        if (allowed && page > 0) {
            targetPage = page;
            targetSetAt = Motion.now();
            steppedFrom = -1;
            stepTowardsTarget(view);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        View view = view();
        if (view != null && scrollY != 0 && (targetPage <= 0 || targetPage == view.page())) {
            if (scrollY < 0) {
                goTo(view, view.page() + 1, view.hasNext());
            } else {
                goTo(view, view.page() - 1, view.hasPrev());
            }
        }
        return true;
    }
}
