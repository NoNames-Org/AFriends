package dev.no.name.afriends.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupPermissionsTest {
    @Test
    void defaultsMatchServer() {
        assertEquals(287, GroupPermissions.DEFAULT);
        assertEquals(2047, GroupPermissions.ALL);
    }

    @Test
    void childPermissionNeedsSend() {
        int noSend = GroupPermissions.toggle(GroupPermissions.DEFAULT, GroupPermissions.SEND);
        assertFalse(GroupPermissions.has(noSend, GroupPermissions.SEND));
        assertFalse(GroupPermissions.has(noSend, GroupPermissions.GIFS), "без отправки сообщений гифки тоже нельзя");
        assertTrue(GroupPermissions.has(noSend, GroupPermissions.ADD), "не подправо — действует само по себе");

        int back = GroupPermissions.toggle(noSend, GroupPermissions.SEND);
        assertTrue(GroupPermissions.has(back, GroupPermissions.GIFS), "подправа возвращаются вместе с главным");
    }

    @Test
    void onlyGifsOff() {
        int perms = GroupPermissions.toggle(GroupPermissions.DEFAULT, GroupPermissions.GIFS);
        assertTrue(GroupPermissions.has(perms, GroupPermissions.SEND));
        assertTrue(GroupPermissions.has(perms, GroupPermissions.IMAGES));
        assertFalse(GroupPermissions.has(perms, GroupPermissions.GIFS));
    }
}
