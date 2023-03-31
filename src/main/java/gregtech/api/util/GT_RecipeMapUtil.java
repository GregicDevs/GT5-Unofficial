package gregtech.api.util;

import static gregtech.api.util.GT_Config.getStackConfigName;
import static gregtech.api.util.GT_Utility.isArrayEmptyOrNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import cpw.mods.fml.common.Loader;
import gregtech.api.interfaces.IGT_RecipeMap;

/**
 * Define helpers useful in the creation of recipe maps.
 */
// Do not place arbitrary stuff here! These are all statically imported in GT_Recipe.java file.
public class GT_RecipeMapUtil {

    public static final Function<GT_Recipe, GT_Recipe> ALL_FAKE_RECIPE = r -> {
        r.mFakeRecipe = true;
        return r;
    };

    public static final Function<GT_Recipe, String> FIRST_FLUID_INPUT = r -> isArrayEmptyOrNull(r.mFluidInputs) ? null
            : r.mFluidInputs[0].getFluid().getName();
    public static final Function<GT_Recipe, String> FIRST_FLUID_OUTPUT = r -> isArrayEmptyOrNull(r.mFluidInputs) ? null
            : r.mFluidOutputs[0].getFluid().getName();
    public static final Function<GT_Recipe, String> FIRST_FLUIDSTACK_INPUT = r -> isArrayEmptyOrNull(r.mFluidInputs)
            ? null
            : r.mFluidInputs[0].getUnlocalizedName();
    public static final Function<GT_Recipe, String> FIRST_FLUIDSTACK_OUTPUT = r -> isArrayEmptyOrNull(r.mFluidOutputs)
            ? null
            : r.mFluidOutputs[0].getUnlocalizedName();
    public static final Function<GT_Recipe, String> FIRST_ITEM_INPUT = r -> isArrayEmptyOrNull(r.mInputs) ? null
            : getStackConfigName(r.mInputs[0]);
    public static final Function<GT_Recipe, String> FIRST_ITEM_OUTPUT = r -> isArrayEmptyOrNull(r.mOutputs) ? null
            : getStackConfigName(r.mOutputs[0]);
    public static final Function<GT_Recipe, String> FIRST_ITEM_OR_FLUID_INPUT = r -> isArrayEmptyOrNull(r.mInputs)
            ? getStackConfigName(r.mInputs[0])
            : isArrayEmptyOrNull(r.mFluidInputs) ? null : r.mFluidInputs[0].getFluid().getName();
    public static final Function<GT_Recipe, String> FIRST_ITEM_OR_FLUID_OUTPUT = r -> isArrayEmptyOrNull(r.mOutputs)
            ? getStackConfigName(r.mOutputs[0])
            : isArrayEmptyOrNull(r.mFluidOutputs) ? null : r.mFluidOutputs[0].getFluid().getName();
    private static final Map<String, IGT_RecipeMap> addonRecipeMaps = new HashMap<>();
    private static final Multimap<String, Consumer<IGT_RecipeMap>> delayedActions = ArrayListMultimap.create();

    public static <T> T[] appendArray(T[] arr, T val) {
        T[] newArr = Arrays.copyOf(arr, arr.length + 1);
        newArr[arr.length] = val;
        return newArr;
    }

    public static GT_RecipeTemplate asTemplate(GT_Recipe r) {
        return asTemplate(r, false);
    }

    public static GT_RecipeTemplate asTemplate(GT_Recipe r, boolean includeTemplate) {
        return new GT_RecipeTemplate(r, includeTemplate);
    }

    public static GT_RecipeBuilder handleCoilHeat(GT_RecipeBuilder b) {
        return b.specialValue(b.getMetadata(GT_RecipeConstants.COIL_HEAT));
    }

    public static List<GT_Recipe> buildRecipeForMultiblock(GT_RecipeBuilder b) {
        List<ItemStack> itemInputs = new ArrayList<>(Arrays.asList(b.getItemInputsBasic()));
        List<ItemStack> itemOutputs = new ArrayList<>(Arrays.asList(b.getItemOutputs()));
        List<FluidStack> fluidInputs = new ArrayList<>(Arrays.asList(b.getFluidInputs()));
        List<FluidStack> fluidOutputs = new ArrayList<>(Arrays.asList(b.getFluidOutputs()));
        cellToFluid(itemInputs, fluidInputs, true);
        cellToFluid(itemInputs, fluidInputs, false);
        return buildOrEmpty(
                b.itemInputs(itemInputs.toArray(new ItemStack[0])).itemOutputs(itemOutputs.toArray(new ItemStack[0]))
                        .fluidInputs(fluidInputs.toArray(new FluidStack[0]))
                        .fluidOutputs(fluidOutputs.toArray(new FluidStack[0])));
    }

    public static List<GT_Recipe> buildRecipeForMultiblockNoCircuit(GT_RecipeBuilder b) {
        List<ItemStack> itemInputs = new ArrayList<>(Arrays.asList(b.getItemInputsBasic()));
        List<ItemStack> itemOutputs = new ArrayList<>(Arrays.asList(b.getItemOutputs()));
        List<FluidStack> fluidInputs = new ArrayList<>(Arrays.asList(b.getFluidInputs()));
        List<FluidStack> fluidOutputs = new ArrayList<>(Arrays.asList(b.getFluidOutputs()));
        cellToFluid(itemInputs, fluidInputs, false);
        cellToFluid(itemInputs, fluidInputs, false);
        return buildOrEmpty(
                b.itemInputs(itemInputs.toArray(new ItemStack[0])).itemOutputs(itemOutputs.toArray(new ItemStack[0]))
                        .fluidInputs(fluidInputs.toArray(new FluidStack[0]))
                        .fluidOutputs(fluidOutputs.toArray(new FluidStack[0])));
    }

    private static void cellToFluid(List<ItemStack> items, List<FluidStack> fluids, boolean removeIntegratedCircuit) {
        for (ListIterator<ItemStack> iterator = items.listIterator(items.size()); iterator.hasPrevious();) {
            ItemStack item = iterator.previous();
            if (GT_Utility.getFluidForFilledItem(item, true) != null || GT_Utility.isCellEmpty(item)
                    || (removeIntegratedCircuit && GT_Utility.isAnyIntegratedCircuit(item))) {
                fluids.add(GT_Utility.convertCellToFluid(item));
                iterator.remove();
            }
        }
    }

    public static List<GT_Recipe> buildOrEmpty(GT_RecipeBuilder builder) {
        return builder.build().map(Collections::singletonList).orElse(Collections.emptyList());
    }

    /**
     * Register a recipe map as part of your mod's public API under your modid and your given identifier.
     *
     * @param identifier
     * @param recipeMap
     * @param dependencies fully qualified identifier of dependent recipe maps. scheduler will only add recipes to one
     *                     of the dependent recipe maps and this recipe map concurrently, guaranteeing thread safety.
     *                     Currently unused, but you are advised to fill them, so that when The Day (tm) comes we don't
     *                     end up with a bunch of weird concurrency bugs.
     */
    public static void registerRecipeMap(String identifier, IGT_RecipeMap recipeMap,
            RecipeMapDependency... dependencies) {
        String modId = Loader.instance().activeModContainer().getModId();
        if ("gregtech".equals(modId)) throw new IllegalStateException(
                "do not register recipe map under the name of gregtech! do it in your own preinit!");
        String id = modId + "@" + identifier;
        addonRecipeMaps.put(id, recipeMap);
        for (Consumer<IGT_RecipeMap> action : delayedActions.get(id)) {
            action.accept(recipeMap);
        }
    }

    /**
     * Use this to register recipes for a recipe map in addon not present at compile time.
     * <p>
     * Do not use this for recipes maps already in {@link GT_RecipeConstants}. None of them will be available via this
     * interface!
     *
     * @param identifier     recipe map id
     * @param registerAction DO NOT ADD RECIPES TO MAPS OTHER THAN THE ONE PASSED TO YOU. DO NOT DO ANYTHING OTHER THAN
     *                       ADDING RECIPES TO THIS R
     */
    public static void registerRecipesFor(String modid, String identifier, Consumer<IGT_RecipeMap> registerAction) {
        String id = modid + "@" + identifier;
        IGT_RecipeMap map = addonRecipeMaps.get(id);
        if (map == null) delayedActions.put(id, registerAction);
        else registerAction.accept(map);
    }

    public static final class GT_RecipeTemplate {

        private final GT_Recipe template;
        private final List<GT_Recipe> derivatives = new ArrayList<>();

        private GT_RecipeTemplate(GT_Recipe template, boolean includeTemplate) {
            this.template = template;
            if (includeTemplate) derivatives.add(template);
        }

        public GT_Recipe derive() {
            GT_Recipe derived = template.copyShallow();
            derivatives.add(derived);
            return derived;
        }

        public List<GT_Recipe> getAll() {
            // fix shallow references
            Set<Object> references = Collections.newSetFromMap(new IdentityHashMap<>());
            for (GT_Recipe r : derivatives) {
                if (!references.add(r.mInputs)) r.mInputs = r.mInputs.clone();
                if (!references.add(r.mOutputs)) r.mOutputs = r.mOutputs.clone();
                if (!references.add(r.mFluidInputs)) r.mFluidInputs = r.mFluidInputs.clone();
                if (!references.add(r.mFluidOutputs)) r.mFluidOutputs = r.mFluidOutputs.clone();
            }
            return derivatives;
        }
    }

    public static final class RecipeMapDependency {

        private final IGT_RecipeMap obj;
        private final String id;

        public RecipeMapDependency(IGT_RecipeMap obj, String id) {
            this.obj = obj;
            this.id = id;
        }

        public static RecipeMapDependency create(String id) {
            return new RecipeMapDependency(null, id);
        }

        public static RecipeMapDependency create(IGT_RecipeMap obj) {
            return new RecipeMapDependency(obj, null);
        }
    }
}