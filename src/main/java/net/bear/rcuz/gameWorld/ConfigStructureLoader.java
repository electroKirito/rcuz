package net.bear.rcuz.gameWorld;

import net.bear.rcuz.RCUZ;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ConfigStructureLoader {
    private final Map<String, StructureTemplate> templateMap = new HashMap<>();

    public void reload(MinecraftServer server) throws IOException {
        templateMap.clear();
        try (Stream<Path> s = Files.list(RCUZ.structureDir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".nbt")).toList()) {
                String id = stripExt(p.getFileName().toString());
                templateMap.put(id, loadTemplate(server, p));
            }
        }
    }

    private StructureTemplate loadTemplate(MinecraftServer server, Path nbtPath) throws IOException {
        RegistryEntryLookup<Block> blockLookup =
                server.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK);

        NbtCompound nbt;
        try (InputStream in = Files.newInputStream(nbtPath)) {
            nbt = NbtIo.readCompressed(in);
        }

        StructureTemplate template = new StructureTemplate();
        template.readNbt(blockLookup, nbt);
        return template;
    }


    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    public StructureTemplate get(String id) {
        return templateMap.get(id);
    }
}
