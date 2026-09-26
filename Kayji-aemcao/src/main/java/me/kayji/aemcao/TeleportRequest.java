package me.kayji.aemcao;

import java.util.UUID;

record TeleportRequest(UUID senderId, UUID targetId, RequestType type, long expiresAtMillis) {
    enum RequestType {
        TPA,
        TPAHERE
    }

    boolean expired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }
}
