package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomFontStoreTest {
    @Test public void acceptsStaticGlyfTrueType() throws Exception {
        File font = createSfnt(0x00010000, new int[]{0x676C7966});
        try {
            assertTrue(CustomFontStore.isRecognizedFontFile(font));
        } finally {
            font.delete();
        }
    }

    @Test public void acceptsOpenTypeAndVariableFontContainersOnAndroid11() throws Exception {
        File cff = createSfnt(0x00010000, new int[]{0x676C7966, 0x43464620});
        File variable = createSfnt(0x00010000, new int[]{0x676C7966, 0x66766172});
        try {
            assertTrue(CustomFontStore.isRecognizedFontFile(cff));
            assertTrue(CustomFontStore.isRecognizedFontFile(variable));
        } finally {
            cff.delete();
            variable.delete();
        }
    }

    @Test public void acceptsCollectionsAndRejectsMalformedContainers() throws Exception {
        File collection = createTtc();
        File malformed = createSfnt(0x74746366, new int[]{0x676C7966}); // "ttcf", but not a TTC
        File truncated = File.createTempFile("font", ".ttf");
        try (FileOutputStream output = new FileOutputStream(truncated)) {
            output.write(new byte[]{0, 1, 0, 0, 0, 1});
        }
        try {
            assertTrue(CustomFontStore.isRecognizedFontFile(collection));
            assertFalse(CustomFontStore.isRecognizedFontFile(malformed));
            assertFalse(CustomFontStore.isRecognizedFontFile(truncated));
        } finally {
            collection.delete();
            malformed.delete();
            truncated.delete();
        }
    }

    private static File createSfnt(int version, int[] tags) throws Exception {
        File file = File.createTempFile("font", ".ttf");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            output.writeInt(version);
            output.writeShort(tags.length);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            int dataOffset = 12 + tags.length * 16;
            for (int tag : tags) {
                output.writeInt(tag);
                output.writeInt(0);
                output.writeInt(dataOffset);
                output.writeInt(0);
            }
        }
        return file;
    }

    private static File createTtc() throws Exception {
        File file = File.createTempFile("font", ".ttc");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            output.writeInt(0x74746366); // "ttcf"
            output.writeInt(0x00010000);
            output.writeInt(1);
            output.writeInt(16);
            output.writeInt(0x00010000);
            output.writeShort(1);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeInt(0x676C7966);
            output.writeInt(0);
            output.writeInt(44);
            output.writeInt(0);
        }
        return file;
    }
}
