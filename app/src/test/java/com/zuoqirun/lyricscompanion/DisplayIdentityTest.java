package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** issue #23：车机把同一块物理屏暴露成多个投屏通道时的识别口径。 */
public class DisplayIdentityTest {
    /** 群里车机的真实现象：同一块屏的两个投屏通道。 */
    private static final String PROJECTION_0 = "shared_fission_bg_XDJAScreenProjection_0";
    private static final String PROJECTION_1 = "shared_fission_bg_XDJAScreenProjection_1";

    private static DisplayIdentity.Screen screen(String name, int width, int height, int dpi) {
        return new DisplayIdentity.Screen(name, width, height, dpi);
    }

    @Test public void stripsTheProjectionChannelSuffixOfTheRealHeadUnitPair() {
        assertEquals("shared_fission_bg_XDJAScreenProjection",
                DisplayIdentity.baseName(PROJECTION_0));
        assertEquals(DisplayIdentity.baseName(PROJECTION_0), DisplayIdentity.baseName(PROJECTION_1));
    }

    @Test public void stripsEveryChannelWritingTheIssueMentions() {
        assertEquals("投影", DisplayIdentity.baseName("投影_0"));
        assertEquals("投影", DisplayIdentity.baseName("投影#1"));
        assertEquals("投影", DisplayIdentity.baseName("投影-2"));
        assertEquals("投影", DisplayIdentity.baseName("投影(3)"));
        assertEquals("投影", DisplayIdentity.baseName("投影（3）"));
        assertEquals("投影", DisplayIdentity.baseName("投影 _ 12"));
        assertEquals("投影", DisplayIdentity.baseName("投影_0 "));
    }

    @Test public void keepsNamesWithoutAChannelNumber() {
        assertEquals("", DisplayIdentity.baseName(null));
        assertEquals("", DisplayIdentity.baseName("   "));
        assertEquals("HDMI screen", DisplayIdentity.baseName("HDMI screen"));
        assertEquals("Built-in Screen", DisplayIdentity.baseName("Built-in Screen"));
        assertEquals("XDJA Screen Projection", DisplayIdentity.baseName("XDJA Screen Projection"));
    }

    @Test public void keepsDigitsThatLookLikePartOfTheName() {
        // 车本身/型号里的数字不是通道号：底座只剩一个字母不再拆，"A4" 不会变成 "A"。
        assertEquals("A4", DisplayIdentity.baseName("A4"));
        assertEquals("A5", DisplayIdentity.baseName("A5"));
        // 通道号只有个位数：四位数字更像年份，不当作通道。
        assertEquals("Track_2024", DisplayIdentity.baseName("Track_2024"));
        // 整个名字就是编号时不拆，免得拆成空名字。
        assertEquals("_0", DisplayIdentity.baseName("_0"));
    }

    @Test public void flagsTheRealHeadUnitPairEvenWithoutResolutionInfo() {
        // 强信号只比名字：一块屏的两个投屏通道即便报出不同分辨率，也该提示。
        DisplayIdentity.Screen first = screen(PROJECTION_0, 0, 0, 0);
        DisplayIdentity.Screen second = screen(PROJECTION_1, 1280, 720, 160);
        assertTrue(DisplayIdentity.likelySamePanel(first, second));
        assertEquals("名称只差通道编号 _0 / _1", DisplayIdentity.duplicateReason(first, second));
    }

    @Test public void identicalNamesCountAsTheSamePanel() {
        assertEquals("名称与分辨率都相同",
                DisplayIdentity.duplicateReason(screen("HDMI screen", 1920, 720, 240),
                        screen("HDMI screen", 1920, 720, 240)));
        // 同名但分辨率对不上时仍然是提示，只是措辞换依据，让用户自己判断。
        assertEquals("屏幕名称完全相同",
                DisplayIdentity.duplicateReason(screen("HDMI screen", 1920, 720, 240),
                        screen("HDMI screen", 1280, 720, 240)));
        // 名字首尾空白不该影响判断。
        assertEquals("名称与分辨率都相同",
                DisplayIdentity.duplicateReason(screen(" HDMI screen ", 1920, 720, 240),
                        screen("HDMI screen", 1920, 720, 240)));
    }

    /** issue #71：两块同名但 Display ID 不同的真屏，只能给「可能是两块屏」的保留提示。 */
    @Test public void identicalNamesWithDifferentIdsAreOnlyHedged() {
        DisplayIdentity.Screen first = new DisplayIdentity.Screen("HDMI screen", 1920, 720, 240, 1);
        DisplayIdentity.Screen second = new DisplayIdentity.Screen("HDMI screen", 1920, 720, 240, 2);
        assertEquals("名称与分辨率都相同，但 Display ID 不同（1 / 2），可能是两块屏",
                DisplayIdentity.duplicateReason(first, second));
        assertEquals("屏幕名称相同但分辨率不同（Display 1 / 2），可能是两块屏",
                DisplayIdentity.duplicateReason(first,
                        new DisplayIdentity.Screen("HDMI screen", 1280, 720, 240, 2)));
        // 只给了一个 id（老调用点）时保持原来的强提示
        assertEquals("名称与分辨率都相同",
                DisplayIdentity.duplicateReason(first,
                        new DisplayIdentity.Screen("HDMI screen", 1920, 720, 240)));
        // 投屏通道对（_0 / _1）即使带 id 也仍然是强提示
        assertEquals("名称只差通道编号 _0 / _1",
                DisplayIdentity.duplicateReason(
                        new DisplayIdentity.Screen(PROJECTION_0, 0, 0, 0, 3),
                        new DisplayIdentity.Screen(PROJECTION_1, 1280, 720, 160, 4)));
    }

    @Test public void differentScreensStaySilent() {
        // 分辨率相同但名字无关：不是同一块屏。
        assertEquals("", DisplayIdentity.duplicateReason(screen("HDMI screen", 1920, 720, 240),
                screen("Rear seat display", 1920, 720, 240)));
        // 名字无关、分辨率也无关：更不该提示。
        assertEquals("", DisplayIdentity.duplicateReason(screen("HDMI screen", 1920, 720, 240),
                screen("Built-in display", 1280, 720, 160)));
        // 少一个字母的短名字不靠包含关系硬凑（"TV" 不该命中 "TVBOX"）。
        assertEquals("", DisplayIdentity.duplicateReason(screen("TV", 1920, 1080, 240),
                screen("TVBOX", 1920, 1080, 240)));
        assertEquals("", DisplayIdentity.duplicateReason(screen("A4", 1920, 720, 240),
                screen("A5", 1920, 720, 240)));
    }

    @Test public void spaceNumberedNamesOnlyHintWhenTheResolutionAgrees() {
        // "Display 1" / "Display 2" 也可能是两块真屏，所以只算弱信号。
        assertEquals("", DisplayIdentity.duplicateReason(screen("Display 1", 1920, 720, 240),
                screen("Display 2", 1280, 720, 240)));
        assertEquals("名称只差尾部编号 1 / 2，分辨率、像素密度也相同",
                DisplayIdentity.duplicateReason(screen("Display 1", 1920, 720, 240),
                        screen("Display 2", 1920, 720, 240)));
        // 像素密度也要一致。
        assertEquals("", DisplayIdentity.duplicateReason(screen("Display 1", 1920, 720, 240),
                screen("Display 2", 1920, 720, 160)));
    }

    @Test public void hyphenNumberedNamesAreReportedWithTheirOwnNumbers() {
        assertEquals("名称只差通道编号 -1 / -2",
                DisplayIdentity.duplicateReason(screen("HDMI-1", 1920, 720, 240),
                        screen("HDMI-2", 1920, 720, 240)));
    }

    @Test public void channelNumberComparisonIgnoresCaseAndTheMarkerItself() {
        // 同一个底座、不同写法的通道号（"_0" 与 "#1"）同样是“名称只差通道编号”的依据。
        assertEquals("名称只差通道编号 _0 / #1",
                DisplayIdentity.duplicateReason(screen("Screen_0", 1920, 720, 240),
                        screen("screen#1", 1280, 720, 240)));
    }

    @Test public void aNameWithoutANumberIsRelatedThroughContainment() {
        // 一方包含另一方，且分辨率、像素密度都一致。
        assertEquals("名称相近且分辨率、像素密度相同",
                DisplayIdentity.duplicateReason(screen("XDJA Screen Projection", 1920, 720, 240),
                        screen("Screen Projection", 1920, 720, 240)));
        assertEquals("", DisplayIdentity.duplicateReason(screen("XDJA Screen Projection", 1920, 720, 240),
                screen("Screen Projection", 1280, 720, 240)));
    }

    @Test public void oneSidedChannelNumberNeedsTheRestToMatch() {
        assertEquals("名称只差尾部编号 _0，分辨率、像素密度也相同",
                DisplayIdentity.duplicateReason(screen("Screen_0", 1920, 720, 240),
                        screen("Screen", 1920, 720, 240)));
        assertEquals("", DisplayIdentity.duplicateReason(screen("Screen_0", 0, 0, 0),
                screen("Screen", 0, 0, 0)));
    }

    @Test public void unknownOrMissingScreensAreHarmless() {
        assertFalse(DisplayIdentity.likelySamePanel(null, screen("Screen", 1920, 720, 240)));
        assertFalse(DisplayIdentity.likelySamePanel(null, null));
        assertEquals("", DisplayIdentity.duplicateReason(screen("Screen", 1920, 720, 240), null));
        assertEquals("", screen(null, 0, 0, 0).name);
        assertFalse(DisplayIdentity.likelySamePanel(screen(null, 0, 0, 0), screen(null, 0, 0, 0)));
        // 空名字 + 空分辨率不能互相“撞车”。
        assertFalse(DisplayIdentity.likelySamePanel(screen("", 1920, 720, 240),
                screen("", 1920, 720, 240)));
    }

    @Test public void theBooleanAndTheReasonAlwaysAgree() {
        DisplayIdentity.Screen[] screens = {
                screen(PROJECTION_0, 1920, 720, 240), screen(PROJECTION_1, 1920, 720, 240),
                screen("Screen Projection", 1920, 720, 240), screen("Display 1", 1920, 720, 240),
                screen("Display 2", 1280, 720, 240), screen("HDMI screen", 1920, 720, 240),
                screen("", 0, 0, 0),
        };
        for (DisplayIdentity.Screen left : screens) {
            for (DisplayIdentity.Screen right : screens) {
                assertEquals(!DisplayIdentity.duplicateReason(left, right).isEmpty(),
                        DisplayIdentity.likelySamePanel(left, right));
            }
        }
    }
}
