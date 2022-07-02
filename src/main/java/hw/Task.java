package hw;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Task {

    @SneakyThrows
    public static void main(String[] args) {
        var url = "https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?sol=16&api_key=UeYzmeoWLc5htFa3a4l1CNK8pMlEFgcnSvFjGzhq";

        Pair<String, Long> imageWithMaxSize = findImageWithMaxSize(urls(getResponseFromHttps(url, Response.BODY)));
        System.out.println(imageWithMaxSize.getLeft() + " : " + imageWithMaxSize.getRight());
    }


    @SneakyThrows
    private static String getResponseFromHttps(@NonNull String sourceUrl, @NonNull Response response) {
        URL url = new URL(sourceUrl);
        @Cleanup var socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(url.getHost(), 443);
        socket.startHandshake();
        @Cleanup var writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
        writer.println("GET " + url.getPath() + "?" + url.getQuery() + " HTTP/1.1");
        writer.println("Host: " + url.getHost());
        writer.println();
        writer.flush();

        @Cleanup var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        var line = "";
        var body = new StringBuilder();
        var respHeaders = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            respHeaders.append(line).append("\n");
            if (line.contains("{") || line.contains("}")) {
                body.append(line);
            }
            if (!body.isEmpty() && line.isBlank()) {
                break;
            }
        }
        return response.equals(Response.HEADERS) ? respHeaders.toString() : body.toString();

    }

    @SneakyThrows
    private static List<String> urls(String json) {
        return new ObjectMapper()
                .readTree(json)
                .findValuesAsText("img_src");
    }

    private static Pair<String, Long> findImageWithMaxSize(List<String> urls){
        return urls.parallelStream()
                .map(Task::getRedirectLink)
                .map(Task::getUrlAndContentSize)
                .max(Comparator.comparing(Pair::getRight))
                .orElseGet(() -> Pair.of("Empty", 0L));
    }


    @SneakyThrows
    private static Pair<String, String> getRedirectLink(String imageUrl) {
        var url = new URL(imageUrl);
        @Cleanup var socket = new Socket(url.getHost(), 80);
        @Cleanup var writer = new PrintWriter(socket.getOutputStream());
        writer.println("GET " + url.getPath() + " HTTP/1.1");
        writer.println("Host: " + url.getHost());
        writer.println();
        writer.flush();

        @Cleanup var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line = "";
        while ((line = reader.readLine()) != null && !line.startsWith("Location: ")) {
        }
        var redirectLink = line.split(" ")[1].trim();
        return Pair.of(imageUrl, redirectLink);
    }

    private static Pair<String, Long> getUrlAndContentSize(Pair<String, String> originalAndRedirectedUrl){
        String contentLength = Arrays.stream(getResponseFromHttps(originalAndRedirectedUrl.getRight(), Response.HEADERS).split("\n"))
                .filter(str -> str.contains("Content-Length: "))
                .findAny()
                .orElse("");
        return Pair.of(originalAndRedirectedUrl.getLeft(), Long.parseLong(contentLength.split(" ")[1]));
    }

}
