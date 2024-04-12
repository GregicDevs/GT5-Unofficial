package gregtech.api.modernmaterials.blocks.dumbbase.special;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import gregtech.api.modernmaterials.ModernMaterial;
import gregtech.api.modernmaterials.blocks.dumbbase.basematerialblock.BaseMaterialBlock;

public class MasterItemBlockRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {

        int materialID = item.getItemDamage();
        BaseMaterialBlock block = (BaseMaterialBlock) Block.getBlockFromItem(item.getItem());

        return block.getBlockEnum()
            .getSpecialBlockRenderAssociatedMaterials()
            .contains(ModernMaterial.getMaterialFromID(materialID));
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    private void repositionItem(ItemRenderType type) {
        switch (type) {
            case EQUIPPED_FIRST_PERSON, EQUIPPED -> GL11.glTranslated(0.5, 0.5, 0.5);
            case FIRST_PERSON_MAP, ENTITY, INVENTORY -> {}
        }
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack itemStack, Object... data) {
        // We will do this for each block, to make life more simple.
        repositionItem(type);

        int materialID = itemStack.getItemDamage();
        BaseMaterialBlock block = (BaseMaterialBlock) Block.getBlockFromItem(itemStack.getItem());

        IItemRenderer itemRenderer = block.getBlockEnum()
            .getItemRenderer(materialID);

        // Delegate this to the appropriate renderer depending on the material.
        itemRenderer.renderItem(type, itemStack, data);
    }
}