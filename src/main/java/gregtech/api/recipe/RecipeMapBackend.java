package gregtech.api.recipe;

import static gregtech.api.recipe.check.FindRecipeResult.NOT_FOUND;
import static gregtech.api.recipe.check.FindRecipeResult.ofSuccess;
import static gregtech.api.util.GT_RecipeBuilder.handleRecipeCollision;
import static gregtech.api.util.GT_RecipeMapUtil.SPECIAL_VALUE_ALIASES;
import static gregtech.api.util.GT_Utility.areStacksEqualOrNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.GregTech_API;
import gregtech.api.interfaces.IRecipeMap;
import gregtech.api.objects.GT_ItemStack;
import gregtech.api.recipe.check.FindRecipeResult;
import gregtech.api.util.GT_OreDictUnificator;
import gregtech.api.util.GT_Recipe;
import gregtech.api.util.GT_RecipeBuilder;
import gregtech.api.util.GT_RecipeConstants;
import gregtech.api.util.MethodsReturnNonnullByDefault;

/**
 * Responsible for recipe addition / search for recipemap.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RecipeMapBackend {

    /**
     * Recipe index based on items.
     */
    private final Map<GT_ItemStack, Collection<GT_Recipe>> itemIndex = new HashMap<>();
    /**
     * Recipe index based on fluids.
     */
    private final Map<String, Collection<GT_Recipe>> fluidIndex = new HashMap<>();

    /**
     * All the recipes belonging to this backend.
     */
    private final Set<GT_Recipe> allRecipes = new HashSet<>();

    private final boolean checkForCollision = true;
    /**
     * List of recipemaps that also receive recipe addition from this backend.
     */
    private final List<IRecipeMap> downstreams = new ArrayList<>(0);

    /**
     * All the properties specific to this backend.
     */
    protected final RecipeMapBackendProperties properties;

    public RecipeMapBackend(RecipeMapBackendPropertiesBuilder propertiesBuilder) {
        this.properties = propertiesBuilder.build();
        GregTech_API.sItemStackMappings.add(itemIndex);
    }

    /**
     * @return Properties specific to this backend.
     */
    public RecipeMapBackendProperties getProperties() {
        return properties;
    }

    /**
     * @return All the recipes belonging to this backend.
     */
    public Set<GT_Recipe> getAllRecipes() {
        return allRecipes;
    }

    /**
     * Adds the supplied to recipe without any check.
     *
     * @return Supplied recipe.
     */
    public GT_Recipe doAdd(GT_Recipe recipe) {
        allRecipes.add(recipe);
        for (FluidStack fluid : recipe.mFluidInputs) {
            if (fluid == null) continue;
            Collection<GT_Recipe> list = fluidIndex.computeIfAbsent(
                fluid.getFluid()
                    .getName(),
                k -> new HashSet<>());
            list.add(recipe);
        }
        return addToItemMap(recipe);
    }

    /**
     * Adds the supplied recipe to the item cache.
     */
    protected GT_Recipe addToItemMap(GT_Recipe recipe) {
        for (ItemStack item : recipe.mInputs) {
            if (item == null) continue;
            GT_ItemStack tStack = new GT_ItemStack(item);
            Collection<GT_Recipe> tList = itemIndex.computeIfAbsent(tStack, k -> new HashSet<>(1));
            tList.add(recipe);
        }
        return recipe;
    }

    /**
     * Builds recipe from supplied recipe builder and adds it.
     */
    public Collection<GT_Recipe> doAdd(GT_RecipeBuilder builder) {
        Iterable<? extends GT_Recipe> recipes = properties.recipeEmitter.apply(builder);
        Collection<GT_Recipe> ret = new ArrayList<>();
        for (GT_Recipe recipe : recipes) {
            if (properties.recipeConfigCategory != null) {
                assert properties.recipeConfigKeyConvertor != null;
                String configKey = properties.recipeConfigKeyConvertor.apply(recipe);
                if (configKey != null && (recipe.mDuration = GregTech_API.sRecipeFile
                    .get(properties.recipeConfigCategory, configKey, recipe.mDuration)) <= 0) {
                    continue;
                }
            }
            if (recipe.mFluidInputs.length < properties.minFluidInputs
                && recipe.mInputs.length < properties.minItemInputs) {
                return Collections.emptyList();
            }
            if (recipe.mSpecialValue == 0) {
                // new style cleanroom/lowgrav handling
                int specialValue = 0;
                if (builder.getMetadata(GT_RecipeConstants.LOW_GRAVITY, false)) specialValue -= 100;
                if (builder.getMetadata(GT_RecipeConstants.CLEANROOM, false)) specialValue -= 200;
                for (GT_RecipeBuilder.MetadataIdentifier<Integer> ident : SPECIAL_VALUE_ALIASES) {
                    Integer metadata = builder.getMetadata(ident, null);
                    if (metadata != null) {
                        specialValue = metadata;
                        break;
                    }
                }
                recipe.mSpecialValue = specialValue;
            }
            if (properties.specialHandler != null) recipe = properties.specialHandler.apply(recipe);
            if (recipe == null) continue;
            if (checkForCollision
                && findRecipeWithResult(recipe.mInputs, recipe.mFluidInputs, null, r -> true, null, false, true)
                    .isSuccessful()) {
                StringBuilder errorInfo = new StringBuilder();
                boolean hasAnEntry = false;
                for (FluidStack fluid : recipe.mFluidInputs) {
                    if (fluid == null) {
                        continue;
                    }
                    String s = fluid.getLocalizedName();
                    if (s == null) {
                        continue;
                    }
                    if (hasAnEntry) {
                        errorInfo.append("+")
                            .append(s);
                    } else {
                        errorInfo.append(s);
                    }
                    hasAnEntry = true;
                }
                for (ItemStack item : recipe.mInputs) {
                    if (item == null) {
                        continue;
                    }
                    String itemName = item.getDisplayName();
                    if (hasAnEntry) {
                        errorInfo.append("+")
                            .append(itemName);
                    } else {
                        errorInfo.append(itemName);
                    }
                    hasAnEntry = true;
                }
                handleRecipeCollision(errorInfo.toString());
                continue;
            }
            ret.add(doAdd(recipe));
        }
        if (!ret.isEmpty()) {
            builder.clearInvalid();
            for (IRecipeMap downstream : downstreams) {
                downstream.doAdd(builder);
            }
        }
        return ret;
    }

    void addDownstream(IRecipeMap downstream) {
        downstreams.add(downstream);
    }

    /**
     * Re-unificates all the items present in recipes.
     */
    public void reInit() {
        itemIndex.clear();
        for (GT_Recipe recipe : allRecipes) {
            GT_OreDictUnificator.setStackArray(true, recipe.mInputs);
            GT_OreDictUnificator.setStackArray(true, recipe.mOutputs);
            addToItemMap(recipe);
        }
    }

    /**
     * @return If supplied item is a valid input for any of the recipes
     */
    public boolean containsInput(ItemStack item) {
        return itemIndex.containsKey(new GT_ItemStack(item)) || itemIndex.containsKey(new GT_ItemStack(item, true));
    }

    /**
     * @return If supplied fluid is a valid input for any of the recipes
     */
    public boolean containsInput(Fluid fluid) {
        return fluidIndex.containsKey(fluid.getName());
    }

    /**
     * Finds a matching recipe.
     * <p>
     * This method is marked as final and package-private, so that any change to it won't break subclasses.
     * Use {@link #overwriteFindRecipe}, {@link #modifyFoundRecipe} or {@link #findFallback} to tweak behavior.
     */
    final FindRecipeResult findRecipeWithResult(ItemStack[] items, FluidStack[] fluids, @Nullable ItemStack specialSlot,
        Predicate<GT_Recipe> recipeValidator, @Nullable GT_Recipe cachedRecipe, boolean notUnificated,
        boolean dontCheckStackSizes) {
        if (doesOverwriteFindRecipe()) {
            return overwriteFindRecipe(items, fluids, specialSlot, recipeValidator, cachedRecipe);
        }

        FindRecipeResult result = doFind(
            items,
            fluids,
            specialSlot,
            recipeValidator,
            cachedRecipe,
            notUnificated,
            dontCheckStackSizes);
        if (result.isSuccessful()) {
            return modifyFoundRecipe(result, items, fluids, specialSlot);
        }
        return findFallback(items, fluids, specialSlot, recipeValidator);
    }

    /**
     * Overwrites {@link #doFind} method. Also override {@link #doesOverwriteFindRecipe} to make it work.
     */
    protected FindRecipeResult overwriteFindRecipe(ItemStack[] items, FluidStack[] fluids,
        @Nullable ItemStack specialSlot, Predicate<GT_Recipe> recipeValidator, @Nullable GT_Recipe cachedRecipe) {
        return NOT_FOUND;
    }

    /**
     * @return Whether to use {@link #overwriteFindRecipe} for finding recipe.
     */
    protected boolean doesOverwriteFindRecipe() {
        return false;
    }

    /**
     * Modifies successfully found recipe.
     */
    protected FindRecipeResult modifyFoundRecipe(FindRecipeResult result, ItemStack[] items, FluidStack[] fluids,
        @Nullable ItemStack specialSlot) {
        return result;
    }

    /**
     * Called when {@link #doFind} cannot find recipe.
     */
    protected FindRecipeResult findFallback(ItemStack[] items, FluidStack[] fluids, @Nullable ItemStack specialSlot,
        Predicate<GT_Recipe> recipeValidator) {
        return NOT_FOUND;
    }

    /**
     * Actual logic to find recipe.
     */
    private FindRecipeResult doFind(ItemStack[] items, FluidStack[] fluids, @Nullable ItemStack specialSlot,
        Predicate<GT_Recipe> recipeValidator, @Nullable GT_Recipe cachedRecipe, boolean notUnificated,
        boolean dontCheckStackSizes) {
        if (allRecipes.isEmpty()) {
            return NOT_FOUND;
        }

        // Some recipe classes require a certain amount of inputs of certain kinds. Like "at least 1 fluid + 1 item"
        // or "at least 2 items" before they start searching for recipes.
        // This improves performance massively, especially when people leave things like programmed circuits,
        // molds or shapes in their machines.
        if (GregTech_API.sPostloadFinished) {
            if (properties.minFluidInputs > 0) {
                int count = 0;
                for (FluidStack fluid : fluids) if (fluid != null) count++;
                if (count < properties.minFluidInputs) {
                    return NOT_FOUND;
                }
            }
            if (properties.minItemInputs > 0) {
                int count = 0;
                for (ItemStack item : items) if (item != null) count++;
                if (count < properties.minItemInputs) {
                    return NOT_FOUND;
                }
            }
        }

        // Unification happens here in case the item input isn't already unificated.
        if (notUnificated) {
            items = GT_OreDictUnificator.getStackArray(true, (Object[]) items);
        }

        // Check the recipe which has been used last time in order to not have to search for it again, if possible.
        if (cachedRecipe != null) {
            if (!cachedRecipe.mFakeRecipe && cachedRecipe.mCanBeBuffered
                && cachedRecipe.isRecipeInputEqual(false, dontCheckStackSizes, fluids, items)) {
                if (!properties.specialSlotSensitive
                    || areStacksEqualOrNull((ItemStack) cachedRecipe.mSpecialItems, specialSlot)) {
                    if (cachedRecipe.mEnabled && recipeValidator.test(cachedRecipe)) {
                        return ofSuccess(cachedRecipe);
                    }
                }
            }
        }

        // Now look for the recipes inside the item index, but only when the recipes actually can have items inputs.
        if (!itemIndex.isEmpty()) {
            for (ItemStack item : items) {
                if (item == null) continue;
                Collection<GT_Recipe> nonWildcardRecipes = itemIndex.get(new GT_ItemStack(item));
                if (nonWildcardRecipes != null) {
                    Optional<GT_Recipe> recipeCandidate = loopRecipes(
                        nonWildcardRecipes,
                        items,
                        fluids,
                        specialSlot,
                        recipeValidator,
                        dontCheckStackSizes);
                    if (recipeCandidate.isPresent()) {
                        return ofSuccess(recipeCandidate.get());
                    }
                }
                Collection<GT_Recipe> wildcardRecipes = itemIndex.get(new GT_ItemStack(item, true));
                if (wildcardRecipes != null) {
                    Optional<GT_Recipe> recipeCandidate = loopRecipes(
                        wildcardRecipes,
                        items,
                        fluids,
                        specialSlot,
                        recipeValidator,
                        dontCheckStackSizes);
                    if (recipeCandidate.isPresent()) {
                        return ofSuccess(recipeCandidate.get());
                    }
                }
            }
        }

        // If the minimum amount of items required for the recipes is 0, then it could match to fluid-only recipes,
        // so check fluid index too.
        if (properties.minItemInputs == 0) {
            for (FluidStack fluid : fluids) {
                if (fluid == null) continue;
                Collection<GT_Recipe> recipes = fluidIndex.get(
                    fluid.getFluid()
                        .getName());
                if (recipes != null) {
                    Optional<GT_Recipe> recipeCandidate = loopRecipes(
                        recipes,
                        items,
                        fluids,
                        specialSlot,
                        recipeValidator,
                        dontCheckStackSizes);
                    if (recipeCandidate.isPresent()) {
                        return ofSuccess(recipeCandidate.get());
                    }
                }
            }
        }

        // And nothing has been found.
        return NOT_FOUND;
    }

    private Optional<GT_Recipe> loopRecipes(Collection<GT_Recipe> recipes, ItemStack[] items, FluidStack[] fluids,
        @Nullable ItemStack specialSlot, Predicate<GT_Recipe> recipeValidator, boolean dontCheckStackSizes) {
        for (GT_Recipe recipe : recipes) {
            if (!recipe.mFakeRecipe && recipe.isRecipeInputEqual(false, dontCheckStackSizes, fluids, items)) {
                if (!properties.specialSlotSensitive
                    || areStacksEqualOrNull((ItemStack) recipe.mSpecialItems, specialSlot)) {
                    if (recipe.mEnabled && recipeValidator.test(recipe)) {
                        return Optional.of(recipe);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    public interface BackendCreator<B extends RecipeMapBackend> {

        /**
         * @see RecipeMapBackend#RecipeMapBackend
         */
        B create(RecipeMapBackendPropertiesBuilder propertiesBuilder);
    }
}