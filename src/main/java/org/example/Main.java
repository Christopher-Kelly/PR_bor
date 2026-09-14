package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] argv) throws IOException, URISyntaxException, InterruptedException {
        // take the url from command line
        if (argv.length <1){
            throw new RuntimeException(
                    "Please enter a PR url as a parameter");
        }
        String PR_Url = argv[0];
        GitHub gh_handler = new GitHub(PR_Url);
        String diff = gh_handler.generatePRDiff();
        System.out.println(diff);
        String review = Anthropic.sendMessage(diff);
        System.out.println("logging review: \n" + review);
        HttpResponse<?> resp = gh_handler.postComment(review);

        if (resp.statusCode() >= 300) {   // note: 201, not 200
            throw new RuntimeException(
                    "GitHub comment failed: " + resp.statusCode() + " — " + resp.body());
        }



    }
}
