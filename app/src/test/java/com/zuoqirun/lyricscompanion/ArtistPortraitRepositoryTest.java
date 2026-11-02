package com.zuoqirun.lyricscompanion;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArtistPortraitRepositoryTest {
    @Test public void bothOfficialImageHostsAreAllowedWithoutAcceptingLookalikes() {
        assertTrue(ArtistPortraitRepository.safeImage("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/photo.jpg"));
        assertTrue(ArtistPortraitRepository.safeImage("https://upload.wikimedia.org/wikipedia/commons/a/photo.jpg"));
        assertFalse(ArtistPortraitRepository.safeImage("https://thumb.wikimedia.org.example.com/photo.jpg"));
        assertFalse(ArtistPortraitRepository.safeImage("http://upload.wikimedia.org/photo.jpg"));
    }

    @Test public void albumDescriptionsCannotMasqueradeAsAnArtist() throws Exception {
        JSONObject entity = new JSONObject("{\"descriptions\":{\"en\":{\"value\":\"album by a famous singer\"}}}");
        assertFalse(ArtistPortraitRepository.musician(entity));
        entity.put("claims", new JSONObject("{\"P31\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q5\"}}}}]}"));
        entity.getJSONObject("descriptions").getJSONObject("en").put("value", "Taiwanese singer-songwriter");
        assertTrue(ArtistPortraitRepository.musician(entity));
    }

    @Test public void exactArtistLabelsAndAliasesAvoidSubstringMatches() throws Exception {
        JSONObject entity = new JSONObject("{\"labels\":{\"en\":{\"value\":\"Jay Chou\"}},"
                + "\"aliases\":{\"zh\":[{\"value\":\"周杰伦\"}]}}");
        assertTrue(ArtistPortraitRepository.matches("Jay Chou", entity));
        assertTrue(ArtistPortraitRepository.matches("周杰伦", entity));
        assertFalse(ArtistPortraitRepository.matches("Jay", entity));
        assertFalse(ArtistPortraitRepository.matches("周杰伦/费玉清", entity));
    }
    @Test public void newArtworkAndAudioSettingsRemainScreenAndStyleScoped() {
        for (String key : new String[]{ArtistPortraitRepository.SOURCE, ArtistPortraitRepository.FIT,
                ArtistPortraitRepository.INTERVAL, AudioVisualRenderer.MODE, AudioVisualRenderer.FPS}) {
            assertTrue(AppPreferences.isStyleScopedKey(key));
        }
    }
}
