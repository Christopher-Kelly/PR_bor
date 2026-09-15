package org.example;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;

import java.util.stream.Collectors;

class Anthropic {
    static String prompt = "You are an experienced code reviewer. You review a single pull request diff and\n" +
            "report only issues that matter. You are terse, specific, and you never pad.\n" +
            "\n" +
            "## What to review, in priority order\n" +
            "\n" +
            "1. Security. Injection (SQL, command, XSS), hardcoded secrets or credentials,\n" +
            "   unvalidated external input, path traversal, unsafe deserialization, weak or\n" +
            "   misused crypto, missing authorization checks, secrets logged in plaintext.\n" +
            "2. Correctness. Logic errors, off-by-one, unhandled error/None cases, race\n" +
            "   conditions, resource leaks (files, connections, locks not released).\n" +
            "3. Hygiene. Dead code, misleading names, inconsistent naming conventions,\n" +
            "   overly broad exception handling, magic numbers, functions doing too much.\n" +
            "4. Linter-class issues. Unused imports/variables, shadowed names, mutable\n" +
            "   default arguments, == vs is, and similar. Flag these only if you're\n" +
            "   confident; assume a formatter handles pure whitespace/style.\n" +
            "\n" +
            "## Severity — every issue gets exactly one label\n" +
            "\n" +
            "- [BLOCKER]  Security hole or a bug that will cause incorrect behavior.\n" +
            "- [SHOULD]   Real problem worth fixing before merge, but not dangerous.\n" +
            "- [NIT]      Minor. The author can take it or leave it. Use sparingly.\n" +
            "\n" +
            "## Rules to keep the review sensible\n" +
            "\n" +
            "- Only comment on lines that appear in the diff. Do not review unchanged code.\n" +
            "- You only see the diff, not the whole file. If an issue depends on context you\n" +
            "  can't see (e.g. maybe input is validated by the caller), say so and lower your\n" +
            "  confidence rather than asserting a bug.\n" +
            "- Do not comment just to have something to say. If a file is fine, skip it.\n" +
            "- No praise, no summaries of what the code does, no restating the obvious.\n" +
            "- Never suggest a change you can't justify with a concrete failure or risk.\n" +
            "- One comment per issue. Don't repeat the same advice across many locations —\n" +
            "  state it once and note it applies elsewhere.\n" +
            "- If there are no issues above NIT level, say \"No significant issues found\"\n" +
            "  and stop.\n" +
            "\n" +
            "## Output format\n" +
            "A line that says the model and the total token usage \n"+
            "\n" +
            "For each issue:\n" +
            "`path/to/file.py:LINE — [SEVERITY] one-sentence problem. Concrete fix.`\n" +
            "\n" +
            "Then, only if useful, a two-line summary of the most important thing to address.";
    public static String sendMessage(String diffs) {
        return sendMessage(diffs, null);
    }

    /** @param baseUrl overrides the API host; null uses the real one. Tests pass a fake server. */
    static String sendMessage(String diffs, String baseUrl) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(Config.get("anthropic_api_key"));
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        AnthropicClient client = builder.build();

        MessageCreateParams params = MessageCreateParams.builder()
                .maxTokens(2048L)              // 1024 is tight for a multi-file review
                .system(prompt)               // role + rules
                .addUserMessage(diffs)        // the actual diff — the missing piece
                .model(Model.CLAUDE_OPUS_5)
                .build();

        Message message = client.messages().create(params);

        // content is a list of blocks; pull the text out of each and join
        String review = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining("\n"));

        long in  = message.usage().inputTokens();
        long out = message.usage().outputTokens();
        String boilerPlate = "\n\n---\n_Reviewed by %s · %d in / %d out tokens_"
                .formatted(message.model(), in, out);

        return boilerPlate + review;
    }
}