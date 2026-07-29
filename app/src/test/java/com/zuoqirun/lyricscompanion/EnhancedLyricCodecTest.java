package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnhancedLyricCodecTest {
    @Test public void extractsQrcCdataAndTimedContent() {
        assertEquals("ABC123", QrcLyricCodec.encryptedContent(
                "<content><![CDATA[ABC123]]></content>", "content"));
        String enhanced = QrcLyricCodec.toEnhancedTimeline(
                "[1000,2000]你(1000,500)好(1500,500)世界(2000,1000)");
        LrcTimeline.At at = LrcTimeline.parse("", "", enhanced).at(1_750L);
        assertEquals("你", at.completedLyric);
        assertEquals("好", at.currentWord);
        assertEquals(500, at.wordProgressPermille);
    }

    @Test public void convertsKrcRelativeWordsToAbsoluteTimeline() {
        String enhanced = KrcLyricCodec.toEnhancedTimeline(
                "[1000,2000]<0,500,0>你<500,500,0>好<1000,1000,0>世界");
        LrcTimeline.At at = LrcTimeline.parse("", "", enhanced).at(1_750L);
        assertEquals("你", at.completedLyric);
        assertEquals("好", at.currentWord);
        assertEquals(500, at.wordProgressPermille);
    }

    @Test public void convertsQrcTranslationToOrdinaryLrc() {
        String translated = QrcLyricCodec.toPlainLrc(
                "[1000,2000]你(1000,500)好(1500,500)\n[4000,1000]下一句(4000,1000)");
        LrcTimeline.At at = LrcTimeline.parse("[00:01.00]Hello\n[00:04.00]Next",
                translated).at(1_200L);
        assertEquals("你好", at.translatedLyric);
    }

    @Test public void qqPlainContentTsDoesNotRequireQrcDecryption() throws Exception {
        assertEquals("[00:01.00]中文翻译",
                QrcLyricCodec.decryptToLrc("[00:01.00]中文翻译"));
    }

    @Test public void extractsKrcLanguageTranslationByLineOrder() {
        String krc = "[language:QUJD]\n[1000,1000]<0,1000,0>Hello\n"
                + "[3000,1000]<0,1000,0>Next";
        assertEquals("QUJD", KrcLyricCodec.encodedLanguage(krc));
        String json = "{\"content\":[{\"type\":1,\"language\":0,"
                + "\"lyricContent\":[[\"你好\"],[\"下一句\"]]}]}";
        String translated = KrcLyricCodec.toTranslationLrc(krc, json);
        assertEquals("你好", LrcTimeline.parse("[00:01.00]Hello\n[00:03.00]Next",
                translated).at(1_200L).translatedLyric);
        assertEquals("下一句", LrcTimeline.parse("[00:01.00]Hello\n[00:03.00]Next",
                translated).at(3_200L).translatedLyric);
    }
}
