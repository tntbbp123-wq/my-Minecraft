package com.tntbbp.myminecraft.manager;

import com.tntbbp.myminecraft.MyMinecraftPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** /tpa, /tpaccept, /tpdeny 요청을 관리한다. */
public class TeleportRequestManager {

    public record Request(UUID requester, long expiresAtMillis) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private final MyMinecraftPlugin plugin;
    private final Map<UUID, Request> pendingByTarget = new HashMap<>();

    public TeleportRequestManager(MyMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    public int timeoutSeconds() {
        return plugin.getConfig().getInt("tpa.request-timeout-seconds", 30);
    }

    public void createRequest(UUID requester, UUID target) {
        long expiresAt = System.currentTimeMillis() + (timeoutSeconds() * 1000L);
        pendingByTarget.put(target, new Request(requester, expiresAt));
    }

    /** 대기 중인(만료되지 않은) 요청을 반환한다. 없거나 만료됐으면 null. */
    public Request getValidRequest(UUID target) {
        Request request = pendingByTarget.get(target);
        if (request == null) {
            return null;
        }
        if (request.isExpired()) {
            pendingByTarget.remove(target);
            return null;
        }
        return request;
    }

    public void clearRequest(UUID target) {
        pendingByTarget.remove(target);
    }
}
