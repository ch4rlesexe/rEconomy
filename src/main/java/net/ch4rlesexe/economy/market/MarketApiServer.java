package net.ch4rlesexe.economy.market;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.ch4rlesexe.economy.REconomyPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MarketApiServer {
    private final REconomyPlugin plugin;
    private final MarketService market;
    private final Gson gson = new Gson();
    private HttpServer server;

    public MarketApiServer(REconomyPlugin plugin, MarketService market) {
        this.plugin = plugin;
        this.market = market;
    }

    public void start() {
        String host = plugin.getConfig().getString("market.api.host", "0.0.0.0");
        int port = plugin.getConfig().getInt("market.api.port", 18880);
        String token = plugin.getConfig().getString("market.api.token", "");
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/market", exchange -> {
                if (!authorize(exchange, token)) {
                    write(exchange, 401, "{\"error\":\"unauthorized\"}");
                    return;
                }
                String method = exchange.getRequestMethod();
                if (method.equalsIgnoreCase("GET")) {
                    List<MarketService.Item> items = market.all();
                    write(exchange, 200, gson.toJson(items));
                    return;
                }
                if (method.equalsIgnoreCase("POST")) {
                    JsonObject body = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
                    String material = body.get("material").getAsString().toUpperCase();
                    if (body.has("price")) market.setPrice(material, body.get("price").getAsDouble(), true);
                    if (body.has("base")) market.setBase(material, body.get("base").getAsDouble());
                    if (body.has("stock")) market.setStock(material, body.get("stock").getAsInt());
                    if (body.has("frozen")) market.setFrozen(material, body.get("frozen").getAsBoolean());
                    write(exchange, 200, gson.toJson(market.get(material).orElse(null)));
                    return;
                }
                write(exchange, 405, "{\"error\":\"method\"}");
            });
            server.start();
            plugin.getLogger().info("Market API listening on " + host + ":" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start Market API: " + e.getMessage());
        }
    }

    private boolean authorize(HttpExchange exchange, String token) {
        if (token == null || token.isBlank() || token.equals("CHANGE_ME_API_TOKEN")) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.equals("Bearer " + token);
    }

    private void write(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }
}
