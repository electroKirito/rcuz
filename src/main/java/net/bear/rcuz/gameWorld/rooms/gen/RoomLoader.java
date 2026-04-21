package net.bear.rcuz.gameWorld.rooms.gen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bear.rcuz.RCUZ;
import net.bear.rcuz.gameWorld.rooms.model.JsonAdapters;
import net.bear.rcuz.gameWorld.rooms.model.RoomTemplate;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RoomLoader {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new JsonAdapters.IdentifierJsonAdapter())
            .registerTypeAdapter(NbtCompound.class, new JsonAdapters.NbtCompoundJsonAdapter())
            .create();

    public static int totalWitght = 0;

    public List<RoomTemplate> loadAll() throws IOException {
        List<RoomTemplate> result = new ArrayList<>();

        try (Stream<Path> stream = Files.list(RCUZ.roomDir)) {

            /// Получение Нужных Path исключительно по .json
            List<Path> files = stream
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();

            for (Path path : files) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    RoomTemplate room = gson.fromJson(reader, RoomTemplate.class);
                    result.add(room);
                }
            }
        }

        totalWitght = 0;
        for (RoomTemplate roomTemplate : result) {
            totalWitght += roomTemplate.weight();
        }
        return result;
    }
}
