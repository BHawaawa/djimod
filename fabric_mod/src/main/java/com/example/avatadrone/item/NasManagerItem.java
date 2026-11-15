package com.example.avatadrone.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Authorized-only NAS management console. Credentials are checked against config/nas.properties. */
public final class NasManagerItem extends Item {
    public NasManagerItem(Settings settings) { super(settings.maxCount(1)); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new com.example.avatadrone.client.NasManagerScreen());
        } else user.sendMessage(Text.literal("NAS 管理器：请输入配置文件中的授权账号"), true);
        return TypedActionResult.success(stack, world.isClient);
    }
}
