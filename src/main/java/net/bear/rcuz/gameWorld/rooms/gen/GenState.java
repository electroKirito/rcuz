package net.bear.rcuz.gameWorld.rooms.gen;

import net.bear.rcuz.gameWorld.rooms.model.OpenSocket;
import net.bear.rcuz.gameWorld.rooms.model.PlacedRoom;

import java.util.*;

public class GenState {
    Map<String, Integer> usedCount = new HashMap<>();
    List<PlacedRoom> placed = new ArrayList<>();
    Deque<OpenSocket> frontier = new ArrayDeque<>(); // куда можно пристыковать новую комнату
    Random random;
    String currentMicroBiome = "none";
}
