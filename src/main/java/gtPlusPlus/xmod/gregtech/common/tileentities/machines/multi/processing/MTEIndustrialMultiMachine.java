package gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTUtility.validMTEList;
import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.TAE;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMultiInput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.metadata.CompressionTierKey;
import gregtech.api.util.GTLanguageManager;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.pollution.PollutionConfig;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import gtPlusPlus.core.block.ModBlocks;
import gtPlusPlus.core.util.minecraft.PlayerUtils;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSolidifier;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class MTEIndustrialMultiMachine extends GTPPMultiBlockBase<MTEIndustrialMultiMachine>
    implements ISurvivalConstructable {

    private final static int MACHINEMODE_METAL = 0;
    private final static int MACHINEMODE_FLUID = 1;
    private final static int MACHINEMODE_MISC = 2;

    private static final int MODE_COMPRESSOR = 0;
    private static final int MODE_LATHE = 1;
    private static final int MODE_MAGNETIC = 2;
    private static final int MODE_FERMENTER = 3;
    private static final int MODE_FLUIDEXTRACT = 4;
    private static final int MODE_EXTRACTOR = 5;
    private static final int MODE_LASER = 6;
    private static final int MODE_AUTOCLAVE = 7;
    private static final int MODE_FLUIDSOLIDIFY = 8;
    private static final int[][] MODE_MAP = new int[][] { { 0, 1, 2 }, { 3, 4, 5 }, { 6, 7, 8 } };
    public static final String[] aToolTipNames = new String[9];
    private int mCasing;
    private static IStructureDefinition<MTEIndustrialMultiMachine> STRUCTURE_DEFINITION = null;

    static {
        for (int id = 0; id < 9; id++) {
            RecipeMap<?> recipeMap = getRecipeMap(id);
            if (recipeMap != null) {
                String aNEI = GTLanguageManager.getTranslation(getRecipeMap(id).unlocalizedName);
                aToolTipNames[id] = aNEI != null ? aNEI : "BAD NEI NAME (Report to Github)";
            }
        }
    }

    public MTEIndustrialMultiMachine(final int aID, final String aName, final String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEIndustrialMultiMachine(final String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(final IGregTechTileEntity aTileEntity) {
        return new MTEIndustrialMultiMachine(this.mName);
    }

    @Override
    public String getMachineType() {
        return "Nine in One";
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String[] aBuiltStrings = new String[3];
        aBuiltStrings[0] = aToolTipNames[0] + ", " + aToolTipNames[1] + ", " + aToolTipNames[2];
        aBuiltStrings[1] = aToolTipNames[3] + ", " + aToolTipNames[4] + ", " + aToolTipNames[5];
        aBuiltStrings[2] = aToolTipNames[6] + ", " + aToolTipNames[7] + ", " + aToolTipNames[8];

        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(EnumChatFormatting.RED + "DEPRECATED! This machine will be removed in the next major update.")
            .addInfo(EnumChatFormatting.RED + "A variety of multiblocks have been added to replace these machines!")
            .addInfo("250% faster than using single block machines of the same voltage")
            .addInfo("Only uses 80% of the EU/t normally required")
            .addInfo("Processes two items per voltage tier")
            .addInfo("Machine Type: Metal - " + EnumChatFormatting.YELLOW + aBuiltStrings[0] + EnumChatFormatting.RESET)
            .addInfo("Machine Type: Fluid - " + EnumChatFormatting.YELLOW + aBuiltStrings[1] + EnumChatFormatting.RESET)
            .addInfo("Machine Type: Misc - " + EnumChatFormatting.YELLOW + aBuiltStrings[2] + EnumChatFormatting.RESET)
            .addInfo("Read Multi-Machine Manual for extra information")
            .addInfo(
                EnumChatFormatting.AQUA + "You can use Solidifier Hatch to solidify multiple liquids."
                    + EnumChatFormatting.RESET)
            .addPollutionAmount(getPollutionPerSecond(null))
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front Center")
            .addCasingInfoMin("Multi-Use Casings", 6, false)
            .addInputBus("Any Casing", 1)
            .addOutputBus("Any Casing", 1)
            .addInputHatch("Any Casing", 1)
            .addOutputHatch("Any Casing", 1)
            .addEnergyHatch("Any Casing", 1)
            .addMaintenanceHatch("Any Casing", 1)
            .addMufflerHatch("Any Casing", 1)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public IStructureDefinition<MTEIndustrialMultiMachine> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEIndustrialMultiMachine>builder()
                .addShape(
                    mName,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C-C", "CCC" }, { "CCC", "CCC", "CCC" }, }))
                .addElement(
                    'C',
                    buildHatchAdder(MTEIndustrialMultiMachine.class)
                        .atLeast(InputBus, OutputBus, Maintenance, Energy, Muffler, InputHatch, OutputHatch)
                        .casingIndex(getTextureIndex())
                        .dot(1)
                        .buildAndChain(onElementPass(x -> ++x.mCasing, ofBlock(ModBlocks.blockCasings3Misc, 2))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(mName, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(mName, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasing = 0;
        return checkPiece(mName, 1, 1, 0) && mCasing >= 6 && checkHatch();
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return TexturesGtBlock.oMCAIndustrialMultiMachineActive;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return TexturesGtBlock.oMCAIndustrialMultiMachine;
    }

    @Override
    protected int getCasingTextureId() {
        return getTextureIndex();
    }

    @Override
    public int getMaxParallelRecipes() {
        return (2 * GTUtility.getTier(this.getMaxInputVoltage()));
    }

    @Override
    public int getPollutionPerSecond(final ItemStack aStack) {
        switch (machineMode) {
            case MACHINEMODE_METAL -> {
                return PollutionConfig.pollutionPerSecondMultiIndustrialMultiMachine_ModeMetal;
            }
            case MACHINEMODE_FLUID -> {
                return PollutionConfig.pollutionPerSecondMultiIndustrialMultiMachine_ModeFluid;
            }
            default -> {
                return PollutionConfig.pollutionPerSecondMultiIndustrialMultiMachine_ModeMisc;
            }
        }
    }

    public int getTextureIndex() {
        return TAE.getIndexFromPage(2, 2);
    }

    private ItemStack getCircuit(ItemStack[] t) {
        for (ItemStack j : t) {
            if (j.getItem() == GTUtility.getIntegratedCircuit(0)
                .getItem()) {
                if (j.getItemDamage() >= 20 && j.getItemDamage() <= 22) {
                    return j;
                }
            }
        }
        return null;
    }

    private int getCircuitID(ItemStack circuit) {
        int H = circuit.getItemDamage();
        int T = (H == 20 ? 0 : (H == 21 ? 1 : (H == 22 ? 2 : -1)));
        return MODE_MAP[machineMode][T];
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return null;
    }

    @Nonnull
    @Override
    public Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        return Arrays.asList(
            RecipeMaps.compressorRecipes,
            RecipeMaps.latheRecipes,
            RecipeMaps.polarizerRecipes,
            RecipeMaps.fermentingRecipes,
            RecipeMaps.fluidExtractionRecipes,
            RecipeMaps.extractorRecipes,
            RecipeMaps.laserEngraverRecipes,
            RecipeMaps.autoclaveRecipes,
            RecipeMaps.fluidSolidifierRecipes);
    }

    @Override
    public int getRecipeCatalystPriority() {
        return -10;
    }

    private static RecipeMap<?> getRecipeMap(int aMode) {
        if (aMode == MODE_COMPRESSOR) {
            return RecipeMaps.compressorRecipes;
        } else if (aMode == MODE_LATHE) {
            return RecipeMaps.latheRecipes;
        } else if (aMode == MODE_MAGNETIC) {
            return RecipeMaps.polarizerRecipes;
        } else if (aMode == MODE_FERMENTER) {
            return RecipeMaps.fermentingRecipes;
        } else if (aMode == MODE_FLUIDEXTRACT) {
            return RecipeMaps.fluidExtractionRecipes;
        } else if (aMode == MODE_EXTRACTOR) {
            return RecipeMaps.extractorRecipes;
        } else if (aMode == MODE_LASER) {
            return RecipeMaps.laserEngraverRecipes;
        } else if (aMode == MODE_AUTOCLAVE) {
            return RecipeMaps.autoclaveRecipes;
        } else if (aMode == MODE_FLUIDSOLIDIFY) {
            return RecipeMaps.fluidSolidifierRecipes;
        } else {
            return null;
        }
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            private ItemStack lastCircuit = null;
            private int lastMode = -1;

            @Nonnull
            @Override
            protected Stream<GTRecipe> findRecipeMatches(@Nullable RecipeMap<?> map) {
                ItemStack circuit = getCircuit(inputItems);
                if (circuit == null) {
                    return Stream.empty();
                }
                if (!GTUtility.areStacksEqual(circuit, lastCircuit)) {
                    lastRecipe = null;
                    lastCircuit = circuit;
                }
                if (machineMode != lastMode) {
                    lastRecipe = null;
                    lastMode = machineMode;
                }
                RecipeMap<?> foundMap = getRecipeMap(getCircuitID(circuit));
                if (foundMap == null) {
                    return Stream.empty();
                }
                return super.findRecipeMatches(foundMap);
            }

            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                if (recipe.getMetadataOrDefault(CompressionTierKey.INSTANCE, 0) > 0)
                    return CheckRecipeResultRegistry.NO_RECIPE;
                return super.validateRecipe(recipe);
            }
        }.setSpeedBonus(1F / 3.5F)
            .setEuModifier(0.8F)
            .setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    public String getMachineModeName() {
        return translateToLocal("GT5U.GTPP_MULTI_INDUSTRIAL_MULTI_MACHINE.mode." + machineMode);
    }

    @Override
    public String[] getInfoData() {
        String[] data = super.getInfoData();
        ArrayList<String> mInfo = new ArrayList<>(Arrays.asList(data));
        String mode;
        switch (machineMode) {
            case MACHINEMODE_METAL -> mode = translateToLocal("GTPP.multiblock.multimachine.metal");
            case MACHINEMODE_FLUID -> mode = translateToLocal("GTPP.multiblock.multimachine.fluid");
            default -> mode = translateToLocal("GTPP.multiblock.multimachine.misc");
        }
        mInfo.add(mode);
        return mInfo.toArray(new String[0]);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("mInternalMode")) {
            machineMode = aNBT.getInteger("mInternalMode");
        }
        super.loadNBTData(aNBT);
    }

    @Override
    public boolean isInputSeparationEnabled() {
        return true;
    }

    @Override
    protected CheckRecipeResult doCheckRecipe() {

        if (machineMode != MACHINEMODE_MISC || !isInputSeparationEnabled()) {
            return super.doCheckRecipe();
        } else {
            CheckRecipeResult result = CheckRecipeResultRegistry.NO_RECIPE;

            // check crafting input hatches first
            if (supportsCraftingMEBuffer()) {
                for (IDualInputHatch dualInputHatch : mDualInputHatches) {
                    ItemStack[] sharedItems = dualInputHatch.getSharedItems();
                    for (var it = dualInputHatch.inventories(); it.hasNext();) {
                        IDualInputInventory slot = it.next();
                        processingLogic.setInputItems(ArrayUtils.addAll(sharedItems, slot.getItemInputs()));
                        processingLogic.setInputFluids(slot.getFluidInputs());
                        CheckRecipeResult foundResult = processingLogic.process();
                        if (foundResult.wasSuccessful()) {
                            return foundResult;
                        }
                        if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) {
                            // Recipe failed in interesting way, so remember that and continue searching
                            result = foundResult;
                        }
                    }
                }
            }

            // Logic for MTEHatchInput
            for (MTEHatchInput solidifierHatch : mInputHatches) {
                if (solidifierHatch instanceof MTEHatchSolidifier hatch) {
                    List<ItemStack> items = hatch.getNonConsumableItems();
                    FluidStack fluid = solidifierHatch.getFluid();

                    if (items != null && fluid != null) {
                        items.add(GTUtility.getIntegratedCircuit(22));

                        processingLogic.setInputItems(items.toArray(new ItemStack[0]));
                        processingLogic.setInputFluids(fluid);

                        CheckRecipeResult foundResult = processingLogic.process();
                        if (foundResult.wasSuccessful()) {
                            return foundResult;
                        }
                        if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) {
                            // Recipe failed in interesting way, so remember that and continue searching
                            result = foundResult;
                        }
                    }
                }
            }
            processingLogic.clear();
            processingLogic.setInputFluids(getStoredFluids());
            // Default logic
            for (MTEHatchInputBus bus : mInputBusses) {
                if (bus instanceof MTEHatchCraftingInputME) {
                    continue;
                }
                List<ItemStack> inputItems = new ArrayList<>();
                for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
                    ItemStack stored = bus.getStackInSlot(i);
                    if (stored != null) {
                        inputItems.add(stored);
                    }
                }
                if (canUseControllerSlotForRecipe() && getControllerSlot() != null) {
                    inputItems.add(getControllerSlot());
                }
                processingLogic.setInputItems(inputItems.toArray(new ItemStack[0]));
                CheckRecipeResult foundResult = processingLogic.process();
                if (foundResult.wasSuccessful()) {
                    return foundResult;
                }
                if (foundResult != CheckRecipeResultRegistry.NO_RECIPE) {
                    // Recipe failed in interesting way, so remember that and continue searching
                    result = foundResult;
                }
            }

            return result;
        }
    }

    @Override
    public ArrayList<FluidStack> getStoredFluids() {
        ArrayList<FluidStack> rList = new ArrayList<>();
        for (MTEHatchInput tHatch : validMTEList(mInputHatches)) {
            if (tHatch instanceof MTEHatchSolidifier) {
                continue;
            }

            setHatchRecipeMap(tHatch);
            if (tHatch instanceof MTEHatchMultiInput) {
                for (FluidStack tFluid : ((MTEHatchMultiInput) tHatch).getStoredFluid()) {
                    if (tFluid != null) {
                        rList.add(tFluid);
                    }
                }
            } else if (tHatch instanceof MTEHatchInputME) {
                if (tHatch.isValid()) {
                    for (FluidStack fluidStack : ((MTEHatchInputME) tHatch).getStoredFluids()) {
                        if (fluidStack == null) continue;
                        rList.add(fluidStack);
                    }
                }
            } else {
                if (tHatch.getFillableStack() != null) {
                    rList.add(tHatch.getFillableStack());
                }
            }
        }

        return rList;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setInteger("mode", machineMode);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        final NBTTagCompound tag = accessor.getNBTData();
        if (tag.hasKey("mode")) {
            currentTip.add(
                translateToLocal("GT5U.machines.oreprocessor1") + " "
                    + EnumChatFormatting.WHITE
                    + translateToLocal("GT5U.GTPP_MULTI_INDUSTRIAL_MULTI_MACHINE.mode." + tag.getInteger("mode"))
                    + EnumChatFormatting.RESET);
        }
    }

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public void onModeChangeByScrewdriver(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ) {
        setMachineMode(nextMachineMode());
        PlayerUtils
            .messagePlayer(aPlayer, translateToLocalFormatted("GT5U.MULTI_MACHINE_CHANGE", getMachineModeName()));
    }

    @Override
    public int nextMachineMode() {
        mLastRecipe = null;
        if (machineMode == MACHINEMODE_METAL) return MACHINEMODE_FLUID;
        else if (machineMode == MACHINEMODE_FLUID) return MACHINEMODE_MISC;
        else return MACHINEMODE_METAL;
    }

    @Override
    public void setMachineModeIcons() {
        machineModeIcons.clear();
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_LPF_METAL);
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_LPF_FLUID);
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT);
    }
}
