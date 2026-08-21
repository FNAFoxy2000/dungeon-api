package com.dungeonapi;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.fabric.FabricAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SchematicManager {

    private final File schematicDirectory;

    public SchematicManager(File schematicDirectory) {

        this.schematicDirectory = schematicDirectory;

        if (!schematicDirectory.exists()) {
            schematicDirectory.mkdirs();
        }
    }

    public void paste(
            ServerLevel minecraftWorld,
            String schematicName,
            int x,
            int y,
            int z
    ) throws IOException, WorldEditException {

        File schematicFile = new File(
                schematicDirectory,
                schematicName + ".schem"
        );

        if (!schematicFile.exists()) {
            throw new IOException(
                    "No existe el schematic: "
                            + schematicFile.getAbsolutePath()
            );
        }

        ClipboardFormat format =
                ClipboardFormats.findByFile(schematicFile);

        if (format == null) {
            throw new IOException(
                    "WorldEdit no reconoce el formato del schematic."
            );
        }

        Clipboard clipboard;

        try (
                FileInputStream inputStream =
                        new FileInputStream(schematicFile);

                ClipboardReader reader =
                        format.getReader(inputStream)
        ) {

            clipboard = reader.read();
        }

        /*
         * Calculamos el área que ocupa el schematic.
         *
         * El punto indicado por x, y, z corresponde
         * al origen del clipboard.
         */
       BlockVector3 clipboardMinimum =
                clipboard.getRegion().getMinimumPoint();

        BlockVector3 clipboardMaximum =
                clipboard.getRegion().getMaximumPoint();

        BlockVector3 clipboardOrigin =
                clipboard.getOrigin();

        int minX =
                x + clipboardMinimum.x() - clipboardOrigin.x();

        int minY =
                y + clipboardMinimum.y() - clipboardOrigin.y();

        int minZ =
                z + clipboardMinimum.z() - clipboardOrigin.z();

        int maxX =
                x + clipboardMaximum.x() - clipboardOrigin.x() + 1;

        int maxY =
                y + clipboardMaximum.y() - clipboardOrigin.y() + 1;

        int maxZ =
                z + clipboardMaximum.z() - clipboardOrigin.z() + 1;
        /*
         * Eliminamos todas las entidades que estén
         * dentro del área de la dungeon.
         *
         * Los jugadores NO se eliminan.
         */
        AABB dungeonArea = new AABB(
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        );

        int removedEntities = 0;

        for (Entity entity :
                minecraftWorld.getEntities(
                        (Entity) null,
                        dungeonArea,
                        entity -> !(entity instanceof Player)
                )) {

            entity.discard();
            removedEntities++;
        }

        DungeonAPI.LOGGER.info(
                "El area de la dungeon es {} y Se eliminaron {} entidades de la dungeon '{}'.",
                dungeonArea,
                removedEntities,
                schematicName
        );

        /*
         * Adaptamos el mundo de Minecraft a WorldEdit.
         */
        com.sk89q.worldedit.world.World world =
                FabricAdapter.adapt(minecraftWorld);

        /*
         * Pegamos el schematic.
         */
        try (
                EditSession editSession =
                        WorldEdit.getInstance()
                                .newEditSession(world)
        ) {

            Operation operation =
                    new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(x, y, z))
                            .ignoreAirBlocks(false)
                            .build();

            Operations.complete(operation);
        }

        DungeonAPI.LOGGER.info(
                "Schematic '{}' pegado en {} {} {}",
                schematicName,
                x,
                y,
                z
        );
    }
}