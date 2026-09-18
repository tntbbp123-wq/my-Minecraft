package com.tntbbp.myminecraft.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeAuthTest {

    @Test
    void acceptsMatchingBearerToken() {
        BridgeAuth auth = new BridgeAuth("s3cret-token");
        assertTrue(auth.configured());
        assertTrue(auth.matches("Bearer s3cret-token"));
        assertTrue(auth.matches("bearer s3cret-token"));
        assertTrue(auth.matches("  Bearer   s3cret-token  "));
    }

    @Test
    void rejectsWrongOrMalformedHeaders() {
        BridgeAuth auth = new BridgeAuth("s3cret-token");
        assertFalse(auth.matches(null));
        assertFalse(auth.matches(""));
        assertFalse(auth.matches("Bearer"));
        assertFalse(auth.matches("Bearer "));
        assertFalse(auth.matches("Bearer s3cret-toke"));
        assertFalse(auth.matches("Bearer s3cret-token2"));
        assertFalse(auth.matches("Basic s3cret-token"));
        assertFalse(auth.matches("s3cret-token"));
    }

    @Test
    void emptyTokenRejectsEverything() {
        BridgeAuth auth = new BridgeAuth("");
        assertFalse(auth.configured());
        assertFalse(auth.matches("Bearer "));
        assertFalse(auth.matches("Bearer anything"));
        assertFalse(new BridgeAuth(null).matches("Bearer null"));
    }
}
