package net.bear.rcuz.gameWorld.rooms.gen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.bear.rcuz.RCUZ;
import net.bear.rcuz.gameWorld.rooms.model.JsonAdapters;
import net.bear.rcuz.gameWorld.rooms.model.MobSpawnEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class MobLoader {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new JsonAdapters.IdentifierJsonAdapter())
            .registerTypeAdapter(NbtCompound.class, new JsonAdapters.NbtCompoundJsonAdapter())
            .create();

    public Map<String, List<MobSpawnEntry>> loadAll() throws IOException {
        Map<String, List<MobSpawnEntry>> result = new HashMap<>();
        Type listType = new TypeToken<List<MobSpawnEntry>>() {}.getType();

        try (Stream<Path> stream = Files.list(RCUZ.mobTagPresetsDir)) {
            List<Path> files = stream
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();

            for (Path path : files) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    List<MobSpawnEntry> entries;

                    if (root == null || root.isJsonNull()) {
                        continue;
                    } else if (root.isJsonArray()) {
                        entries = gson.fromJson(root, listType);
                    } else if (root.isJsonObject()) {
                        JsonObject obj = root.getAsJsonObject();
                        JsonElement spawnList = null;
                        if (obj.has("spawn")) {
                            spawnList = obj.get("spawn");
                        } else if (obj.has("mobs")) {
                            spawnList = obj.get("mobs");
                        } else if (obj.has("entries")) {
                            spawnList = obj.get("entries");
                        }

                        if (spawnList != null && spawnList.isJsonArray()) {
                            entries = gson.fromJson(spawnList, listType);
                        } else {
                            /// backward-compat: single entry preset
                            MobSpawnEntry single = gson.fromJson(root, MobSpawnEntry.class);
                            entries = single == null ? List.of() : List.of(single);
                        }
                    } else {
                        continue;
                    }

                    entries = entries.stream().filter(Objects::nonNull).toList();
                    if (entries.isEmpty()) continue;

                    String fileName = path.getFileName().toString();
                    int extIndex = fileName.lastIndexOf('.');
                    String presetId = extIndex >= 0 ? fileName.substring(0, extIndex) : fileName;
                    result.put(presetId, entries);
                }
            }
        }

        return result;
    }
}
