package com.dungeonapi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class DungeonAPI implements ModInitializer {

        public static final String MOD_ID = "dungeon-api";
        public static String API_KEY;
        public static int PORT;

        public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

        private static MinecraftServer server;
        private static HttpServer httpServer;
        private static SchematicManager schematicManager;
        private static DungeonManager dungeonManager;

        private static void loadConfiguration() {

                File configFile = new File(
                                "config",
                                "dungeonapi.properties");

                Properties properties = new Properties();

                try {

                        if (!configFile.exists()) {

                                configFile.getParentFile().mkdirs();

                                properties.setProperty(
                                                "port",
                                                "8080");

                                properties.setProperty(
                                                "api-key",
                                                "dungeon-test-123456");

                                try (FileOutputStream output = new FileOutputStream(configFile)) {

                                        properties.store(
                                                        output,
                                                        "Dungeon API Configuration");
                                }

                                LOGGER.info(
                                                "Se ha creado la configuración de Dungeon API en {}",
                                                configFile.getAbsolutePath());
                        }

                        try (FileInputStream input = new FileInputStream(configFile)) {

                                properties.load(input);
                        }

                        API_KEY = properties.getProperty("api-key");

                        if (API_KEY == null || API_KEY.isBlank()) {

                                throw new IllegalStateException(
                                                "La propiedad 'api-key' no está configurada.");
                        }

                        String portString = properties.getProperty("port");

                        try {
                                PORT = Integer.parseInt(portString);
                        } catch (NumberFormatException e) {
                                throw new IllegalStateException(
                                                "El puerto configurado no es válido: " + portString,
                                                e);
                        }

                        LOGGER.info(
                                        "Configuración de Dungeon API cargada correctamente.");
                        LOGGER.info(
                                        "Configuración cargada: puerto={}, api-key configurada={}",
                                        PORT,
                                        API_KEY != null && !API_KEY.isBlank());

                } catch (IOException e) {

                        LOGGER.error(
                                        "No se pudo cargar dungeonapi.properties.",
                                        e);

                        throw new RuntimeException(e);
                }
        }

        @Override
        public void onInitialize() {

                LOGGER.info("Dungeon API iniciándose...");

                loadConfiguration();

                // Comando para probar la API desde el juego
                CommandRegistrationCallback.EVENT.register(
                                (dispatcher, registryAccess, environment) -> {

                                        dispatcher.register(
                                                        Commands.literal("dungeon-api")
                                                                        .requires(source -> source.hasPermission(2))

                                                                        .then(
                                                                                        Commands.literal("reset")
                                                                                                        .then(
                                                                                                                        Commands.argument(
                                                                                                                                        "dungeon",
                                                                                                                                        StringArgumentType
                                                                                                                                                        .word())
                                                                                                                                        .executes(context -> {

                                                                                                                                                String dungeonId = StringArgumentType
                                                                                                                                                                .getString(
                                                                                                                                                                                context,
                                                                                                                                                                                "dungeon");

                                                                                                                                                boolean success = dungeonManager
                                                                                                                                                                .resetDungeon(
                                                                                                                                                                                dungeonId);

                                                                                                                                                return success ? 1
                                                                                                                                                                : 0;
                                                                                                                                        })))

                                                                        .then(
                                                                                        Commands.literal("reset-all")
                                                                                                        .executes(context -> {

                                                                                                                DungeonResetResult result = dungeonManager
                                                                                                                                .resetAllDungeons();

                                                                                                                String message = "Dungeons regeneradas: "
                                                                                                                                + result.getSuccessful()
                                                                                                                                + "/"
                                                                                                                                + result.getTotal();

                                                                                                                server.getPlayerList()
                                                                                                                                .broadcastSystemMessage(
                                                                                                                                                Component.literal(
                                                                                                                                                                message),
                                                                                                                                                false);

                                                                                                                if (result.getFailed() > 0) {

                                                                                                                        String errorMessage = "Dungeons con error: "
                                                                                                                                        + result.getFailed();

                                                                                                                        server.getPlayerList()
                                                                                                                                        .broadcastSystemMessage(
                                                                                                                                                        Component.literal(
                                                                                                                                                                        errorMessage),
                                                                                                                                                        false);
                                                                                                                }

                                                                                                                return result.getSuccessful();
                                                                                                        })));
                                });

                ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {

                        server = serverInstance;

                        LOGGER.info("Servidor de Minecraft iniciado.");

                        schematicManager = new SchematicManager(
                                        server.getServerDirectory()
                                                        .resolve("config")
                                                        .resolve("worldedit")
                                                        .resolve("schematics")
                                                        .toFile());

                        dungeonManager = new DungeonManager(
                                        server,
                                        schematicManager);

                        try {
                                dungeonManager.createDefaultConfiguration();
                        } catch (Exception e) {
                                LOGGER.error(
                                                "No se pudo crear la configuración de las dungeons.",
                                                e);
                        }

                        httpServer = new HttpServer(
                                        server,
                                        schematicManager);

                        try {
                                LOGGER.info(
                                                "Intentando iniciar Dungeon API en el puerto {}...",
                                                PORT);
                                httpServer.start();
                        } catch (Exception e) {
                                LOGGER.error(
                                                "No se pudo iniciar la Dungeon API en el puerto {}.",
                                                PORT,
                                                e);
                        }
                });

                ServerLifecycleEvents.SERVER_STOPPING.register(serverInstance -> {

                        if (httpServer != null) {
                                httpServer.stop();
                                httpServer = null;
                        }

                        server = null;

                        LOGGER.info("Dungeon API detenida.");
                });
        }

        public static MinecraftServer getServer() {
                return server;
        }

        private static int resetDungeon(
                        String schematicName,
                        String worldName,
                        int x,
                        int y,
                        int z) {

                if (server == null || schematicManager == null) {

                        LOGGER.error("La Dungeon API todavía no está preparada.");

                        return 0;
                }

                try {

                        var worldKey = net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.DIMENSION,
                                        net.minecraft.resources.ResourceLocation.parse(worldName));

                        var world = server.getLevel(worldKey);

                        if (world == null) {

                                LOGGER.error(
                                                "No se encontró el mundo '{}'.",
                                                worldName);

                                return 0;
                        }

                        schematicManager.paste(
                                        world,
                                        schematicName,
                                        x,
                                        y,
                                        z);

                        LOGGER.info(
                                        "Dungeon '{}' regenerada en {} {} {} del mundo '{}'.",
                                        schematicName,
                                        x,
                                        y,
                                        z,
                                        worldName);

                        return 1;

                } catch (Exception e) {

                        LOGGER.error(
                                        "Error al regenerar la dungeon '{}'.",
                                        schematicName,
                                        e);

                        return 0;
                }
        }
}