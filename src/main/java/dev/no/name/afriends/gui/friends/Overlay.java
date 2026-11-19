package dev.no.name.afriends.gui.friends;

/**
 * Всплывающее окошко мессенджера (или правило поверх ленты), которое получает событие раньше самого окна.
 * Каждый метод отвечает, забрало ли окошко событие; по умолчанию — не его дело
 */
interface Overlay {
    /** Что делать с событием дальше. */
    enum Result {
        /** Не моё: спросить следующего. */
        PASS,
        /** Забрал: окно отвечает «обработано». */
        CONSUMED,
        /** Забрал, но окно отвечает «не обработано»: дальше событие не идёт. */
        STOPPED;

        static Result of(boolean handled) {
            return handled ? CONSUMED : STOPPED;
        }

        static Result consumedIf(boolean handled) {
            return handled ? CONSUMED : PASS;
        }
    }

    default Result click(double x, double y, boolean right) {
        return Result.PASS;
    }

    default Result key(int key) {
        return Result.PASS;
    }

    default Result type(String text) {
        return Result.PASS;
    }

    default Result scroll(double x, double y, double amount) {
        return Result.PASS;
    }
}
