package com.dungeonapi;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpServer {

        private final net.minecraft.server.MinecraftServer minecraftServer;
        private final SchematicManager schematicManager;

        private com.sun.net.httpserver.HttpServer server;

        private static final int PORT = DungeonAPI.PORT;

        public HttpServer(
                        net.minecraft.server.MinecraftServer minecraftServer,
                        SchematicManager schematicManager) {
                this.minecraftServer = minecraftServer;
                this.schematicManager = schematicManager;
        }

        public void start() throws IOException {

                DungeonAPI.LOGGER.info(
                                "Creando servidor HTTP en 0.0.0.0:{}...",
                                PORT);

                server = com.sun.net.httpserver.HttpServer.create(
                                new InetSocketAddress("0.0.0.0", PORT),
                                0);
                                
                DungeonAPI.LOGGER.info(
                                "Servidor HTTP creado correctamente en 0.0.0.0:{}.",
                                PORT);

                server.createContext(
                                "/dungeon/test",
                                this::handleTest);

                server.createContext(
                                "/dungeon/reset",
                                this::handleReset);

                server.setExecutor(null);

                server.start();

                DungeonAPI.LOGGER.info(
                                "Dungeon API escuchando en el puerto {}",
                                PORT);
        }

        public void stop() {

                if (server != null) {

                        server.stop(0);

                        server = null;

                        DungeonAPI.LOGGER.info(
                                        "Servidor HTTP detenido.");
                }
        }

        private void handleTest(
                        HttpExchange exchange) throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("GET")) {

                        sendResponse(
                                        exchange,
                                        405,
                                        """
                                                        {
                                                            "success": false,
                                                            "message": "Method not allowed"
                                                        }
                                                        """);

                        return;
                }

                String response = """
                                {
                                    "success": true,
                                    "message": "Dungeon API funcionando correctamente"
                                }
                                """;

                sendResponse(exchange, 200, response);
        }

        private void handleReset(
                        HttpExchange exchange) throws IOException {

                if (!exchange.getRequestMethod()
                                .equalsIgnoreCase("POST")) {

                        sendResponse(
                                        exchange,
                                        405,
                                        """
                                                        {
                                                            "success": false,
                                                            "message": "Method not allowed"
                                                        }
                                                        """);

                        return;
                }

                String authorization = exchange.getRequestHeaders()
                                .getFirst("Authorization");

                String expectedAuthorization = "Bearer " + DungeonAPI.API_KEY;

                if (!expectedAuthorization.equals(authorization)) {

                        sendResponse(
                                        exchange,
                                        401,
                                        """
                                                        {
                                                            "success": false,
                                                            "message": "Unauthorized"
                                                        }
                                                        """);

                        return;
                }

                String body;

                try (InputStream inputStream = exchange.getRequestBody()) {

                        body = new String(
                                        inputStream.readAllBytes(),
                                        StandardCharsets.UTF_8);
                }

                String worldName = getJsonString(body, "world");
                String schematicName = getJsonString(body, "schematic");

                Integer x = getJsonInteger(body, "x");
                Integer y = getJsonInteger(body, "y");
                Integer z = getJsonInteger(body, "z");

                if (worldName == null ||
                                schematicName == null ||
                                x == null ||
                                y == null ||
                                z == null) {

                        sendResponse(
                                        exchange,
                                        400,
                                        """
                                                        {
                                                            "success": false,
                                                            "message": "Invalid request. Required fields: world, schematic, x, y, z"
                                                        }
                                                        """);

                        return;
                }

                minecraftServer.execute(() -> {

                        try {

                                var worldKey = net.minecraft.resources.ResourceKey.create(
                                                net.minecraft.core.registries.Registries.DIMENSION,
                                                net.minecraft.resources.ResourceLocation.parse(
                                                                worldName));

                                var world = minecraftServer.getLevel(worldKey);

                                if (world == null) {

                                        DungeonAPI.LOGGER.error(
                                                        "No se encontró el mundo '{}'",
                                                        worldName);

                                        return;
                                }

                                schematicManager.paste(
                                                world,
                                                schematicName,
                                                x,
                                                y,
                                                z);

                                DungeonAPI.LOGGER.info(
                                                "Dungeon '{}' regenerada correctamente.",
                                                schematicName);

                        } catch (Exception e) {

                                DungeonAPI.LOGGER.error(
                                                "Error al pegar el schematic.",
                                                e);
                        }
                });

                sendResponse(
                                exchange,
                                200,
                                """
                                                {
                                                    "success": true,
                                                    "message": "Dungeon reset queued"
                                                }
                                                """);
        }

        private String getJsonString(
                        String json,
                        String key) {

                Pattern pattern = Pattern.compile(
                                "\"" + Pattern.quote(key) +
                                                "\"\\s*:\\s*\"([^\"]*)\"");

                Matcher matcher = pattern.matcher(json);

                if (matcher.find()) {
                        return matcher.group(1);
                }

                return null;
        }

        private Integer getJsonInteger(
                        String json,
                        String key) {

                Pattern pattern = Pattern.compile(
                                "\"" + Pattern.quote(key) +
                                                "\"\\s*:\\s*(-?\\d+)");

                Matcher matcher = pattern.matcher(json);

                if (matcher.find()) {

                        try {
                                return Integer.parseInt(
                                                matcher.group(1));
                        } catch (NumberFormatException ignored) {
                        }
                }

                return null;
        }

        private void sendResponse(
                        HttpExchange exchange,
                        int statusCode,
                        String response) throws IOException {

                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders()
                                .set(
                                                "Content-Type",
                                                "application/json; charset=UTF-8");

                exchange.sendResponseHeaders(
                                statusCode,
                                responseBytes.length);

                try (OutputStream outputStream = exchange.getResponseBody()) {

                        outputStream.write(responseBytes);
                }
        }
}