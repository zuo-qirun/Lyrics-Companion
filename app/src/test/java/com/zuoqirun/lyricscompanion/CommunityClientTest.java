package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CommunityClientTest {
    @Test public void onlyUnseenFeedbackRepliesAreReturnedForAutoDisplay() {
        CommunityClient.FeedbackReply first = reply("first");
        CommunityClient.FeedbackReply second = reply("second");
        String readIds = CommunityClient.markFeedbackRepliesRead("", Arrays.asList(first));

        List<CommunityClient.FeedbackReply> unread = CommunityClient.unreadFeedbackReplies(
                Arrays.asList(first, second), readIds);

        assertEquals(1, unread.size());
        assertEquals("second", unread.get(0).id);
    }

    @Test public void displayedRepliesDoNotReappearOnTheNextLaunch() {
        CommunityClient.FeedbackReply reply = reply("reply-id");
        String readIds = CommunityClient.markFeedbackRepliesRead("", Arrays.asList(reply));

        assertTrue(CommunityClient.unreadFeedbackReplies(Arrays.asList(reply), readIds).isEmpty());
    }

    private static CommunityClient.FeedbackReply reply(String id) {
        return new CommunityClient.FeedbackReply(id, "ticket", "2026-08-01T00:00:00Z", "reply");
    }
}
