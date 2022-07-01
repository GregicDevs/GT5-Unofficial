package gregtech.api.metatileentity.implementations;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IGlobalEnergy;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import static gregtech.api.enums.GT_Values.V;

public class GT_MetaTileEntity_Wireless_Dynamo extends GT_MetaTileEntity_Hatch_Dynamo implements IGlobalEnergy {

    static final long ticks_between_energy_addition = 200L;
    static final long number_of_energy_additions = 10L;
    private final long eu_transferred_per_operation = 2L * V[mTier] * ticks_between_energy_addition;
    private String owner_name;

    public GT_MetaTileEntity_Wireless_Dynamo(String aName, byte aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, new String[] {"TESTING 123"}, aTextures);
    }

    public GT_MetaTileEntity_Wireless_Dynamo(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public GT_MetaTileEntity_Wireless_Dynamo(String aName, int aTier, String aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[]{aBaseTexture, Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_WIRELESS_ON[mTier]};
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[]{aBaseTexture, Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_WIRELESS_ON[mTier]};
    }

    @Override
    public boolean isSimpleMachine() {
        return true;
    }

    @Override
    public boolean isFacingValid(byte aFacing) {
        return true;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public boolean isEnetInput() {
        return true;
    }

    @Override
    public boolean isInputFacing(byte aSide) {
        return aSide == getBaseMetaTileEntity().getFrontFacing();
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public long getMinimumStoredEU() {
        return 2 * V[mTier];
    }

    @Override
    public long maxEUInput() {
        return V[mTier];
    }

    @Override
    public long maxEUStore() {
        return V[mTier] * number_of_energy_additions * ticks_between_energy_addition;
    }

    public long getEUCapacity() {
        return 40000L;
    }

    @Override
    public long maxAmperesIn() {
        return 2;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new GT_MetaTileEntity_Wireless_Dynamo(mName, mTier, new String[] {"Idk what this does but apparently it's needed"}, mTextures);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, byte aSide, ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, byte aSide, ItemStack aStack) {
        return false;
    }

    @Override
    public boolean ownerControl() {
        return true;
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity.isServerSide()) {

            // On first tick find the player name and attempt to add them to the map.
            if (aTick == 1) {
                owner_name = this.getBaseMetaTileEntity().getOwnerName();

                if (owner_name == null) {
                    throw new IllegalArgumentException("Wireless energy receiver got a null name.");
                }

                // If the owner is not in the hash map, add them with 0 EU.
                if (!GlobalEnergyMap.containsKey(owner_name)) {
                    GlobalEnergyMap.put(owner_name, 100_000_000L);
                }
            }

            // Every ticks_between_energy_addition ticks change the energy content of the block.
            if (aTick % ticks_between_energy_addition == 0L) {

                long total_eu = GlobalEnergyMap.get(owner_name);
                GlobalEnergyMap.put(owner_name, total_eu + aBaseMetaTileEntity.getStoredEU());
                setEUVar(0L);

            }
        }
    }
}

