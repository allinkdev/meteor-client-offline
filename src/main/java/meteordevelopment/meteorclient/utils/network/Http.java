/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.utils.other.JsonDateDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;

public class Http {
    public static final Set<String> WHITELISTED_DOMAINS = Set.of(
        "login.live.com",
        "user.auth.xboxlive.com",
        "xsts.auth.xboxlive.com",
        "api.minecraftservices.com",
        "api.mojang.com",
        "sessionserver.mojang.com"
    );
    private static final Logger LOGGER = LoggerFactory.getLogger("Meteor HTTP Client");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Date.class, new JsonDateDeserializer())
        .create();

    private enum Method {
        GET,
        POST
    }

    public static class Request {
        private HttpRequest.Builder builder;
        private Method method;

        public Request(Method method, String url) {
            try {
                this.builder = HttpRequest.newBuilder().uri(new URI(url)).header("User-Agent", "Meteor Client");
                this.method = method;
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        public Request bearer(String token) {
            builder.header("Authorization", "Bearer " + token);

            return this;
        }

        public Request bodyString(String string) {
            builder.header("Content-Type", "text/plain");
            builder.method(method.name(), HttpRequest.BodyPublishers.ofString(string));
            method = null;

            return this;
        }

        public Request bodyForm(String string) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            builder.method(method.name(), HttpRequest.BodyPublishers.ofString(string));
            method = null;

            return this;
        }

        public Request bodyJson(String string) {
            builder.header("Content-Type", "application/json");
            builder.method(method.name(), HttpRequest.BodyPublishers.ofString(string));
            method = null;

            return this;
        }

        public Request bodyJson(Object object) {
            builder.header("Content-Type", "application/json");
            builder.method(method.name(), HttpRequest.BodyPublishers.ofString(GSON.toJson(object)));
            method = null;

            return this;
        }

        private <T> T _send(String accept, HttpResponse.BodyHandler<T> responseBodyHandler) {

            builder.header("Accept", accept);
            if (method != null) builder.method(method.name(), HttpRequest.BodyPublishers.noBody());

            try {
                final HttpRequest request = builder.build();
                final URI uri = request.uri();
                final String host = uri.getHost();

                LOGGER.info("Making request to {}!", host);

                if (!WHITELISTED_DOMAINS.contains(host)) {
                    return null;
                }

                var res = CLIENT.send(builder.build(), responseBodyHandler);
                return res.statusCode() == 200 ? res.body() : null;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void send() {
            _send("*/*", HttpResponse.BodyHandlers.discarding());
        }

        public InputStream sendInputStream() {
            return _send("*/*", HttpResponse.BodyHandlers.ofInputStream());
        }

        public String sendString() {
            return _send("*/*", HttpResponse.BodyHandlers.ofString());
        }

        public Stream<String> sendLines() {
            return _send("*/*", HttpResponse.BodyHandlers.ofLines());
        }

        public <T> T sendJson(Type type) {
            InputStream in = _send("application/json", HttpResponse.BodyHandlers.ofInputStream());
            return in == null ? null : GSON.fromJson(new InputStreamReader(in), type);
        }
    }

    public static Request get(String url) {
        return new Request(Method.GET, url);
    }

    public static Request post(String url) {
        return new Request(Method.POST, url);
    }
}
