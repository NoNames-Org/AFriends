package dev.no.name.blooddesigner.core.battlepass;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Магазин bloodstone: главное меню и раздел «Боевой пропуск». Узнаём по ключам перевода в заголовке. */
public final class ShopMenu {
    private static final String STORE_KEY = "bloodstone.inventoryview.store";
    private static final String BATTLEPASS_KEY = "bloodstone.inventoryview.store_battlepass";
    /** В главном меню магазина кнопка раздела «Боевой пропуск». */
    public static final int STORE_BATTLEPASS_SLOT = 36;
    public static final int BACK_SLOT = 0;
    private static final int INFO_SLOT = 8;
    private static final int PREMIUM_SLOT = 9;
    private static final int BUNDLE_SLOT = 14;

    /** Товар: слот для покупки, предмет с сервера (название и описание на языке клиента), цена и баланс. */
    public record Product(int slot, ItemStack stack, String name, List<String> lines, int price, int balance) {
        public Product {
            lines = List.copyOf(lines);
        }

        public boolean affordable() {
            return balance >= price;
        }
    }

    public record View(List<Product> products, ItemStack info) {
        public View {
            products = List.copyOf(products);
        }
    }

    private ShopMenu() {
    }

    public static boolean isStore(Component title) {
        return MenuText.hasKey(title, STORE_KEY);
    }

    public static boolean isBattlePassStore(Component title) {
        return MenuText.hasKey(title, BATTLEPASS_KEY);
    }

    public static View read(AbstractContainerMenu menu) {
        List<Product> products = new ArrayList<>();
        for (int slot : new int[]{PREMIUM_SLOT, BUNDLE_SLOT}) {
            ItemStack stack = MenuText.stack(menu, slot);
            if (!stack.isEmpty()) {
                products.add(product(slot, stack));
            }
        }
        return new View(products, MenuText.stack(menu, INFO_SLOT));
    }

    private static Product product(int slot, ItemStack stack) {
        int price = 0;
        int balance = 0;
        List<String> lines = new ArrayList<>();
        for (Component line : MenuText.loreLines(stack)) {
            String priceArg = MenuText.keyArgument(line, ".price");
            String balanceArg = MenuText.keyArgument(line, ".balance");
            if (priceArg != null) {
                price = BattlePassText.firstNumber(priceArg);
            } else if (balanceArg != null) {
                balance = BattlePassText.firstNumber(balanceArg);
            } else if (!MenuText.hasKeySuffix(line, ".buy")) {
                String text = line.getString().strip();
                if (!text.isEmpty()) {
                    lines.add(text);
                }
            }
        }
        return new Product(slot, stack, MenuText.name(stack), lines, Math.max(0, price), Math.max(0, balance));
    }
}
