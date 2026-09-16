package org.example;

import org.example.cloud.config.AnthropicConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;

/**
 * The review pipeline: fetch the PR diff, ask Claude to review it, post the
 * review back as a comment. Kept separate from {@link Main} so it can be run
 * against fake GitHub/Anthropic servers.
 */
public class App {
    /** Same pipeline, but with both API hosts injected — this is what the tests drive. */
    public static void run(String githubApiBase, String anthropicBase,
                           String owner, String repo, int pr, String apiKey, String githubAuthKey)
            throws IOException, URISyntaxException, InterruptedException {
        run(new GitHub(owner, repo, pr, githubApiBase, githubAuthKey), anthropicBase, apiKey);
    }

    static void run(GitHub gh, String anthropicBase, String apiKey)
            throws IOException, URISyntaxException, InterruptedException {

        String diff = gh.generatePRDiff();
        System.out.println(diff);
        Anthropic anthropic = new Anthropic();

        String review = anthropic.sendMessage(diff, anthropicBase, apiKey);
        System.out.println("logging review: \n" + review);

        HttpResponse<?> resp = gh.postComment(review);
        if (resp.statusCode() >= 300) {
            throw new RuntimeException(
                    "GitHub comment failed: " + resp.statusCode() + " — " + resp.body());
        }
    }
}
