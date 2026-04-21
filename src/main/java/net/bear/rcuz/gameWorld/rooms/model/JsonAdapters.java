package net.bear.rcuz.gameWorld.rooms.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;

public class JsonAdapters {

    /// IDENTIFIER ADDAPTER
    public static class IdentifierJsonAdapter implements JsonDeserializer<Identifier> {
        @Override
        public Identifier deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return Identifier.tryParse(jsonElement.toString());
        }
    }

    /// IDENTIFIER ADDAPTER
    public static class NbtCompoundJsonAdapter implements JsonDeserializer<NbtCompound> {
        @Override
        public NbtCompound deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            try {
                return StringNbtReader.parse(jsonElement.toString());
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
