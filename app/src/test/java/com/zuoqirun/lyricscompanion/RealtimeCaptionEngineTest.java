package com.zuoqirun.lyricscompanion;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RealtimeCaptionEngineTest {
    @After public void tearDown() {
        RealtimeCaptionEngine.installLocalRecognizer(null);
        RealtimeCaptionEngine.installCloudRecognizer(null);
    }

    @Test public void fallsBackToCloudOnlyWhenAllowed() throws Exception {
        RealtimeCaptionEngine.installLocalRecognizer(new FakeRecognizer("Local", false));
        RealtimeCaptionEngine.installCloudRecognizer(new FakeCloud("Cloud", true));
        RealtimeCaptionEngine engine = new RealtimeCaptionEngine();
        engine.start(true, new NoopListener());
        assertTrue(engine.isUsingCloud());
        assertEquals("Cloud", engine.activeName());
        engine.stop();
    }

    @Test(expected = IllegalStateException.class)
    public void unavailableLocalDoesNotSilentlyUseCloudWhenDisabled() throws Exception {
        RealtimeCaptionEngine.installLocalRecognizer(new FakeRecognizer("Local", false));
        RealtimeCaptionEngine.installCloudRecognizer(new FakeCloud("Cloud", true));
        new RealtimeCaptionEngine().start(false, new NoopListener());
    }

    @Test public void localRuntimeFailureSwitchesToCloud() throws Exception {
        FakeRecognizer local = new FakeRecognizer("Local", true);
        RealtimeCaptionEngine.installLocalRecognizer(local);
        RealtimeCaptionEngine.installCloudRecognizer(new FakeCloud("Cloud", true));
        RealtimeCaptionEngine engine = new RealtimeCaptionEngine();
        engine.start(true, new NoopListener());
        local.fail();
        assertTrue(engine.isUsingCloud());
        assertEquals("Cloud", engine.activeName());
        engine.stop();
    }

    private static class FakeRecognizer implements LocalSpeechRecognizer {
        private final String name; private final boolean available;
        FakeRecognizer(String name, boolean available) { this.name = name; this.available = available; }
        @Override public boolean isAvailable() { return available; }
        @Override public String displayName() { return name; }
        private Listener listener;
        @Override public void start(Listener listener) { this.listener = listener; }
        @Override public void acceptPcm16(byte[] pcm, int length, int rate) { }
        @Override public void stop() { }
        void fail() { listener.onError(new IllegalStateException("local failed")); }
    }
    private static final class FakeCloud extends FakeRecognizer implements CloudSpeechRecognizer {
        FakeCloud(String name, boolean available) { super(name, available); }
    }
    private static final class NoopListener implements LocalSpeechRecognizer.Listener {
        @Override public void onPartial(String text, String language) { }
        @Override public void onFinal(String text, String language) { }
        @Override public void onError(Throwable error) { }
    }
}
