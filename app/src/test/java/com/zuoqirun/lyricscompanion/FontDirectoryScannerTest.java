package com.zuoqirun.lyricscompanion;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FontDirectoryScannerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void scansNestedFontsAndIgnoresPictures() throws Exception {
        File root = temporary.newFolder("fonts");
        File nested = new File(root, "nested");
        assertTrue(nested.mkdir());
        assertTrue(new File(root, "font.TTF").createNewFile());
        assertTrue(new File(nested, "font.otf").createNewFile());
        assertTrue(new File(root, "wallpaper.png").createNewFile());
        assertEquals(2, FontDirectoryScanner.findFiles(root).size());
    }

    @Test public void acceptsOnlyFontExtensions() {
        assertTrue(FontDirectoryScanner.isFontName("collection.TTC"));
        assertFalse(FontDirectoryScanner.isFontName("font.ttf.png"));
        assertFalse(FontDirectoryScanner.isFontName(null));
    }
}
