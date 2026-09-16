package local;

import org.example.App;

// JUnit 5
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// MockWebServer
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

// for the fixture() helper — reading test resource files
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class ReviewPipelineTest {
    /** A line that exists only in the fixture patch, never in the system prompt. */
    private static final String DIFF_MARKER = "SELECT * FROM users WHERE id =";

    MockWebServer github;
    MockWebServer anthropic;

    @BeforeEach
    void setup() throws IOException {
        github = new MockWebServer();
        anthropic = new MockWebServer();
        github.start();
        anthropic.start();
    }

    @AfterEach
    void teardown() throws IOException {
        github.shutdown();
        anthropic.shutdown();
    }

    private static String fixture(String name) throws IOException {
        String path = "fixtures/" + name;
        try (InputStream in = ReviewPipelineTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fetchesDiff_callsClaude_postsReview() throws Exception {
        // GitHub responds to GET /files, then to POST /comments
        github.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("pr-files.json")));
        github.enqueue(new MockResponse().setResponseCode(201));

        // Claude responds with a canned review
        anthropic.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("claude-review.json")));

        // run the real pipeline against both fakes
        App.run(github.url("/").toString(),
                anthropic.url("/").toString(),
                "owner", "repo", 1,"ww","www");

        // 1. did we ask GitHub for the right thing?
        RecordedRequest filesReq = github.takeRequest();
        assertEquals("GET", filesReq.getMethod());
        assertTrue(filesReq.getPath().contains("/pulls/1/files"));

        // 2. did we send Claude the diff (not just the prompt)?
        RecordedRequest claudeReq = anthropic.takeRequest();
        String claudeBody = claudeReq.getBody().readUtf8();
        assertTrue(claudeBody.contains(DIFF_MARKER), "diff should reach Claude");
        assertFalse(claudeBody.contains("lockfileVersion"),
                "lockfile churn should be stripped before it costs us tokens");

        // 3. did we post the canned review to the RIGHT endpoint?
        RecordedRequest postReq = github.takeRequest();
        assertEquals("POST", postReq.getMethod());
        assertTrue(postReq.getPath().contains("/issues/1/comments"),
                "summary comment must use issues endpoint, not pulls");
        assertTrue(postReq.getBody().readUtf8().contains("BLOCKER"),
                "canned review text should end up in the comment body");
    }
}
