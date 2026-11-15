package dev.no.name.blooddesigner.api;

import java.util.List;

// заявки в друзья от дополнения: строки сверху списка с кнопками «принять» и «отклонить»
public interface FriendRequests {
    List<String> names();

    // подпись под ником в строке заявки
    String hint();

    void respond(String name, boolean accept);
}
