package main.java.org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", 8080), 0);

        server.createContext("/", Main::loadPage);
        server.createContext("/api/marcas/", Main::searchBrands);

        server.start();
    }

    private static void searchBrands(HttpExchange exchange) throws IOException {
        final String[] vehicleTypes = {"carros", "motos", "caminhoes"};
        Map<String, String> values = readForm(exchange);
        String vehicleType = values.getOrDefault("tipo-de-veiculo", "").trim();

        if (!match(vehicleTypes, vehicleType)) {
            throw new IllegalArgumentException("Tipo inválido de veiculos");
        }

        String url = "https://brasilapi.com.br/api/fipe/marcas/v1/" + vehicleType;
        HttpResponse<String> response = request(url);

        if (response.statusCode() == 200 && response.body() != null) {
            response(exchange, 200, response.body());
        } else {
            throw new RuntimeException();
        }
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8
        );

        Map<String, String> dados = new HashMap<>();
        for (String field : body.split("&")) {
            String[] partes = field.split("=", 2);
            String nome = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length == 2
                    ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            dados.put(nome, valor);
        }
        return dados;
    }

    private static boolean match(String[] list, String value) {
        for (int i = 0; i < list.length; i++) {
            if (list[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void response(HttpExchange exchange, int status, String content) throws IOException {
        response(exchange, status, content, "application/json");
    }

    private static void response(HttpExchange exchange, int status, String content, String type) throws IOException {
        byte[] responseContent = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.sendResponseHeaders(status, responseContent.length);
        exchange.getResponseBody().write(responseContent);
        exchange.close();
    }

    private static void response(HttpExchange exchange, int status) throws IOException {
        response(exchange, status);
    }

    private static HttpResponse<String> request(String url) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return response;
            } else {
                System.out.println("Não foi possivel consultar o tipo!");
            }
        } catch (IOException | InterruptedException error) {
            throw new RuntimeException();
        }
        return null;
    }

    private static void loadPage(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            response(exchange, 405, "Metodo não permitido", "text/plain");
            return;
        }
        try (InputStream arquivo = Main.class.getResourceAsStream("/public/index.html")) {
            if (arquivo == null) {
                response(exchange, 404, "Pagina não econtrada", "text/plain");
                return;
            }
            byte[] pagina = arquivo.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, pagina.length);
            exchange.getResponseBody().write(pagina);
            exchange.close();
        }
    }
}