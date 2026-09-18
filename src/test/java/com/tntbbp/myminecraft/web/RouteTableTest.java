package com.tntbbp.myminecraft.web;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTableTest {

    private RouteTable<String> table() {
        RouteTable<String> table = new RouteTable<>();
        table.add("GET", "/players/online", false, "online");
        table.add("GET", "/players/{uuid}/profile", false, "profile");
        table.add("GET", "/players/{uuid}/mail", false, "mail-list");
        table.add("POST", "/players/{uuid}/mail/{id}/recall", false, "recall");
        table.add("POST", "/commands/complete", true, "complete");
        table.add("POST", "/commands/execute", false, "execute");
        table.add("GET", "/news/{id}", false, "news-get");
        table.add("GET", "/news/ai-draft", false, "draft-literal");
        table.add("PATCH", "/news/{id}", false, "news-patch");
        table.add("DELETE", "/server/countdown", false, "cancel");
        return table;
    }

    @Test
    void matchesLiteralAndVariableSegments() {
        RouteTable<String> table = table();
        assertEquals("online", table.match("GET", "/players/online").route().handler());

        RouteTable.Match<String> profile = table.match("GET", "/players/54988c7d-0000-0000-0000-000000000000/profile");
        assertEquals("profile", profile.route().handler());
        assertEquals("54988c7d-0000-0000-0000-000000000000", profile.params().get("uuid"));

        RouteTable.Match<String> recall = table.match("post", "/players/abc/mail/1012/recall");
        assertEquals("recall", recall.route().handler());
        assertEquals("abc", recall.params().get("uuid"));
        assertEquals("1012", recall.params().get("id"));
    }

    @Test
    void prefersMoreLiteralSegments() {
        RouteTable<String> table = table();
        assertEquals("draft-literal", table.match("GET", "/news/ai-draft").route().handler());
        assertEquals("news-get", table.match("GET", "/news/7").route().handler());
    }

    @Test
    void methodMustMatch() {
        RouteTable<String> table = table();
        assertNull(table.match("POST", "/players/online"));
        assertEquals("news-patch", table.match("PATCH", "/news/7").route().handler());
        assertNull(table.match("GET", "/unknown"));
        assertNull(table.match("GET", "/players/x/profile/extra"));
    }

    @Test
    void writeFlag() {
        RouteTable<String> table = table();
        assertFalse(table.match("GET", "/players/online").route().write());
        assertFalse(table.match("POST", "/commands/complete").route().write());
        assertTrue(table.match("POST", "/commands/execute").route().write());
        assertTrue(table.match("PATCH", "/news/1").route().write());
        assertTrue(table.match("DELETE", "/server/countdown").route().write());
    }

    @Test
    void apiPathStripsPrefixAndDecodes() {
        assertEquals("/players/online", WebBridge.apiPath(URI.create("http://127.0.0.1:25590/v1/players/online")));
        assertEquals("/players/online", WebBridge.apiPath(URI.create("http://127.0.0.1:25590/v1/players/online/")));
        assertEquals("/stocks/custom_금광",
                WebBridge.apiPath(URI.create("http://127.0.0.1:25590/v1/stocks/custom_%EA%B8%88%EA%B4%91")));
        assertEquals("/", WebBridge.apiPath(URI.create("http://127.0.0.1:25590/v1")));
        assertNull(WebBridge.apiPath(URI.create("http://127.0.0.1:25590/health")));
        assertNull(WebBridge.apiPath(URI.create("http://127.0.0.1:25590/v10/health")));
    }
}
