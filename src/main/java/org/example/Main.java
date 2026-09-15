package org.example;

import java.io.IOException;
import java.net.URISyntaxException;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] argv) throws IOException, URISyntaxException, InterruptedException {
        // take the url from command line
        if (argv.length <1){
            throw new RuntimeException(
                    "Please enter a PR url as a parameter");
        }
        String DEFAULT_API_BASE = "https://api.github.com";
        App.run(new GitHub(argv[0], DEFAULT_API_BASE), null);
    }
}
