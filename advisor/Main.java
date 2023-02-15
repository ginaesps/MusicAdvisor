package advisor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

public class Main {
    static String authCode = "";
    static String idclient = "c4e995c9c69f4a0181163b09ffe36e8a";
    static String clientsecret = "97fc1045a6584254ac9c87508188eae5";
    static String token = "";
    static String apiPath = "https://api.spotify.com";
    static List<String> result;
    static int pageEntries = 5;
    static int currentPage = 0;
    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String authLink = "/authorize?client_id=c4e995c9c69f4a0181163b09ffe36e8a&redirect_uri=http://localhost:8080/&response_type=code";
        String authPath = "https://accounts.spotify.com";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-access")) {
                authPath = args[i + 1];
            }
            if (args[i].equals("-resource")) {
                apiPath = args[i + 1];
            }
            if (args[i].equals("-page")) {
                pageEntries = Integer.parseInt(args[i + 1]);
            }
        }

        authLink = authPath + authLink;

        Boolean auth = false;

        String[] option = scanner.nextLine().split(" ");

        while (true) {
            if (auth) {
                    switch (option[0]) {
                        case "playlists":
                            String category = "";
                            for (int i = 1; i < option.length; i++) {
                                category += option[i] + " ";
                            }
                            result = retrievePlaylists(category);
                            currentPage = 0;
                            printRequest();
                            break;
                        case "featured":
                            result = retrieveFeatured();
                            currentPage = 0;
                            printRequest();
                            break;
                        case "new":
                            result = retrieveNew();
                            currentPage = 0;
                            printRequest();
                            break;
                        case "categories":
                            result = retrieveCategories();
                            currentPage = 0;
                            printRequest();
                            break;
                        case "prev":
                            currentPage--;
                            printRequest();
                            break;
                        case "next":
                            currentPage++;
                            printRequest();
                            break;
                        case "exit":
                            result = null;
                            currentPage = 0;
                            break;
                    }
            } else {
                switch (option[0]) {
                    case "auth":
                        System.out.println("use this link to request the access code:");
                        System.out.println(authLink);
                        System.out.println("waiting for code...");
                        HttpServer server = HttpServer.create();
                        server.bind(new InetSocketAddress(8080), 0);

                        server.createContext("/",
                                new HttpHandler() {
                                    public void handle(HttpExchange exchange) throws IOException {
                                        String query = exchange.getRequestURI().getQuery();
                                        String hello;

                                        if (query != null && query.contains("code")) {
                                            String code = query.split("code=")[1];
                                            hello = "Got the code. Return back to your program.";
                                            Main.authCode = code;
                                            exchange.sendResponseHeaders(200, hello.length());
                                        } else {
                                            hello = "Authorization code not found. Try again.";
                                            exchange.sendResponseHeaders(400, hello.length());
                                        }

                                        exchange.getResponseBody().write(hello.getBytes());
                                        exchange.getResponseBody().close();
                                    }
                                }
                        );

                        server.start();
                        while (Main.authCode.equals("")) {
                            Thread.sleep(10);
                        }
                        server.stop(1);

                        HttpClient client = HttpClient.newBuilder().build();

                        HttpRequest request = HttpRequest.newBuilder()
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((idclient + ":" + clientsecret).getBytes()))
                                .uri(URI.create(authPath + "/api/token"))
                                .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&code=" + authCode + "&redirect_uri=http://localhost:8080/"))
                                .build();

                        HttpResponse<String>  response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        //System.out.println("response: \n" + response.body());
                        JsonObject jo = JsonParser.parseString(response.body()).getAsJsonObject();
                        token = jo.get("access_token").getAsString();
                        System.out.println("code received");
                        System.out.println("Making http request for access_token...");
                        System.out.println("Success!");
                        auth = true;
                        break;

                    default:
                        System.out.println("Please, provide access for application.");
                        break;
                }
            }
            option = scanner.nextLine().split(" ");
        }
    }

    public static JsonObject makeRequest(String urlLink) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + token)
                .uri(URI.create(apiPath + urlLink))
                .GET()
                .build();

        HttpResponse<String>  response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject resp = JsonParser.parseString(response.body()).getAsJsonObject();
        return resp;
    }

    private static boolean errorCheck(JsonObject jo) {
        if (jo.has("error")) {
            String message = jo.getAsJsonObject("error").get("message").getAsString();
            System.out.println(message);
            return true;
        }
        return false;
    }

    public static void printRequest() throws IOException, InterruptedException {
        if (result == null || result.size() == 0) {
            return;
        }
        if (currentPage * pageEntries >= result.size() || currentPage < 0) {
            System.out.println("No more pages.");
            if (currentPage < 0) {
                currentPage = 0;
            } else {
                currentPage = (result.size() - 1) / pageEntries;
            }
            return;
        }

        for (int i = currentPage * pageEntries; i < (currentPage + 1) * pageEntries; i++) {
            if (i < result.size()) {
                System.out.println(result.get(i));
            }
        }

        System.out.printf("---PAGE %d OF %d--- \n", currentPage + 1, (result.size() - 1) / pageEntries + 1);
    }

    public static List<String> retrieveCategories() throws IOException, InterruptedException {
        JsonObject categories = makeRequest("/v1/browse/categories");
        errorCheck(categories);
        List<String> catList = new ArrayList<>();

        for (JsonElement category : categories.getAsJsonObject("categories").getAsJsonArray("items")) {
            catList.add(category.getAsJsonObject().get("name").getAsString());
        }

        return catList;
    }

    public static List<String> printPlayLists(JsonObject jo) {
        if (jo.has("playlists")) {
            jo = jo.getAsJsonObject("playlists");
            List<String> playlists = new ArrayList<>();
            for (JsonElement playlist : jo.getAsJsonArray("items")) {
                JsonObject plst0 = playlist.getAsJsonObject();
                String name = plst0.get("name").getAsString();
                String link = plst0.getAsJsonObject("external_urls").get("spotify").getAsString();
                playlists.add(name + "\n" + link + "\n");
            }
            return playlists;
        }
        return null;
    }

    public static List<String> retrievePlaylists(String category) throws IOException, InterruptedException {
        JsonObject playlists = makeRequest("/v1/browse/categories");
        errorCheck(playlists);
        playlists = playlists.getAsJsonObject("categories");
        String catID = "";

        List<String> playList = new ArrayList<>();
        for (JsonElement cat : playlists.getAsJsonArray("items")) {
            JsonObject cat0 = cat.getAsJsonObject();
            String name = cat0.get("name").getAsString();
            if (name.equals(category.trim())) {
                catID = cat0.get("id").getAsString();
                break;
            }
        }

        if (catID.isBlank()) {
            System.out.println("Unknown category name.");
        } else {
            playlists = makeRequest("/v1/browse/categories/" + catID + "/playlists");
            errorCheck(playlists);
             playList = printPlayLists(playlists);
        }

        return playList;
    }

    public static List<String> retrieveFeatured() throws IOException, InterruptedException {
        JsonObject playlists = makeRequest("/v1/browse/featured-playlists");
        errorCheck(playlists);
        List<String> featList = new ArrayList<>();
        featList = printPlayLists(playlists);
        return featList;
    }

    public static List<String> retrieveNew() throws IOException, InterruptedException {
        JsonObject releases = makeRequest("/v1/browse/new-releases");
        errorCheck(releases);
        releases = releases.getAsJsonObject("albums");

        List<String> albums = new ArrayList<>();
        for (JsonElement album : releases.getAsJsonArray("items")) {
            JsonObject alb = album.getAsJsonObject();
            String name = alb.get("name").getAsString();
            String link = alb.getAsJsonObject("external_urls").get("spotify").getAsString();

            List<String> artists = new ArrayList<>();
            for (JsonElement artist : alb.getAsJsonArray("artists")) {
                artists.add(artist.getAsJsonObject().get("name").getAsString());
            }

            albums.add(name + "\n" + artists + "\n" + link + "\n");
        }

        return albums;
    }
}