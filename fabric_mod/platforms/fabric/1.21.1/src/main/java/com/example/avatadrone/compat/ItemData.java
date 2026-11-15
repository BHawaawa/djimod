package com.example.avatadrone.compat;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import com.mojang.serialization.Dynamic;

/** CUSTOM_DATA is immutable: every mutation commits a fresh component for inventory sync and saves. */
public final class ItemData {
    private ItemData() {}
    public static boolean has(ItemStack stack) { return stack.contains(DataComponentTypes.CUSTOM_DATA); }
    public static NbtCompound read(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? null : data.copyNbt();
    }
    public static NbtCompound copy(ItemStack stack) {
        NbtCompound data = read(stack);
        return data == null ? new NbtCompound() : data;
    }
    public static void set(ItemStack stack, NbtCompound data) {
        if (data == null || data.isEmpty()) stack.remove(DataComponentTypes.CUSTOM_DATA);
        else stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }
    public static void update(ItemStack stack, Consumer<NbtCompound> edit) {
        NbtCompound data = copy(stack);
        edit.accept(data);
        set(stack, data);
    }
    public static void remove(ItemStack stack, String key) { update(stack, data -> data.remove(key)); }
    public static void put(ItemStack stack, String key, NbtElement value) { update(stack, data -> data.put(key, value)); }
    public static void putDouble(ItemStack stack, String key, double value) { update(stack, data -> data.putDouble(key, value)); }
    public static void putLong(ItemStack stack, String key, long value) { update(stack, data -> data.putLong(key, value)); }
    public static void putBoolean(ItemStack stack, String key, boolean value) { update(stack, data -> data.putBoolean(key, value)); }
    public static void putString(ItemStack stack, String key, String value) { update(stack, data -> data.putString(key, value)); }
    public static void putUuid(ItemStack stack, String key, UUID value) { update(stack, data -> data.putUuid(key, value)); }
    public static ItemStack decode(RegistryWrapper.WrapperLookup registries, NbtCompound data) {
        NbtCompound migrated = data;
        if (data.contains("Count")) {
            // Nested mod-owned item stacks are not automatically upgraded by vanilla's world fixer.
            migrated = (NbtCompound) net.minecraft.datafixer.Schemas.getFixer().update(
                    net.minecraft.datafixer.TypeReferences.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, data.copy()),
                    3465, net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId()).getValue();
        }
        return ItemStack.fromNbtOrEmpty(registries, migrated);
    }
}
