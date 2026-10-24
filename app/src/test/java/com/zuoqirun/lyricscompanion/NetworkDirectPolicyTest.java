package com.zuoqirun.lyricscompanion;

import java.net.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NetworkDirectPolicyTest {
    @Test public void defaultOffKeepsTheSystemProxyPath() {
        NetworkDirectPolicy.reset();
        assertFalse(NetworkDirectPolicy.direct());
        assertNull(NetworkDirectPolicy.proxy());
    }

    @Test public void directModeBypassesTheSystemProxy() {
        NetworkDirectPolicy.reset();
        NetworkDirectPolicy.apply(true);
        assertTrue(NetworkDirectPolicy.direct());
        assertSame(Proxy.NO_PROXY, NetworkDirectPolicy.proxy());
    }

    @Test public void turningDirectOffRestoresTheDefaultPath() {
        NetworkDirectPolicy.reset();
        NetworkDirectPolicy.apply(true);
        NetworkDirectPolicy.apply(false);
        assertFalse(NetworkDirectPolicy.direct());
        assertNull(NetworkDirectPolicy.proxy());
    }

    @Test public void failuresAreCountedAndClearedByModeSwitches() {
        NetworkDirectPolicy.reset();
        assertEquals(0, NetworkDirectPolicy.failureCount());
        NetworkDirectPolicy.noteFailure();
        NetworkDirectPolicy.noteFailure();
        NetworkDirectPolicy.noteFailure();
        assertEquals(3, NetworkDirectPolicy.failureCount());
        NetworkDirectPolicy.apply(false);
        assertEquals(0, NetworkDirectPolicy.failureCount());
        NetworkDirectPolicy.reset();
        assertEquals(0, NetworkDirectPolicy.failureCount());
    }

    @Test public void failureCountIsCappedForTheDiagnosticLine() {
        NetworkDirectPolicy.reset();
        int overflow = NetworkDirectPolicy.MAX_FAILURES + 5;
        for (int i = 0; i < overflow; i++) NetworkDirectPolicy.noteFailure();
        assertEquals(NetworkDirectPolicy.MAX_FAILURES, NetworkDirectPolicy.failureCount());
    }

    @Test public void hintOnlyAppearsWhenFailuresHappenedAndDirectModeIsOff() {
        NetworkDirectPolicy.reset();
        assertTrue(NetworkDirectPolicy.diagnosticHint().isEmpty());
        NetworkDirectPolicy.noteFailure();
        String hint = NetworkDirectPolicy.diagnosticHint();
        assertTrue(hint.contains("网络直连"));
        assertTrue(hint.contains("1"));
        NetworkDirectPolicy.apply(true);
        assertTrue(NetworkDirectPolicy.diagnosticHint().isEmpty());
    }

    @Test public void aSuccessfulRequestClearsTheHint() {
        NetworkDirectPolicy.reset();
        NetworkDirectPolicy.noteFailure();
        NetworkDirectPolicy.noteFailure();
        assertEquals(2, NetworkDirectPolicy.failureCount());
        NetworkDirectPolicy.noteSuccess();
        assertEquals(0, NetworkDirectPolicy.failureCount());
        assertTrue(NetworkDirectPolicy.diagnosticHint().isEmpty());
    }
}
