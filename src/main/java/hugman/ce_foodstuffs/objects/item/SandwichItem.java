package hugman.ce_foodstuffs.objects.item;

import com.mojang.datafixers.util.Pair;
import hugman.ce_foodstuffs.init.CEFItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SandwichItem extends Item {
	public SandwichItem(Settings settings) {
		super(settings);
	}

	public static void addIngredient(ItemStack sandwich, Item item) {
		CompoundTag compoundTag = sandwich.getOrCreateTag();
		for(int index = 1; index <= 3; index++) {
			Item ingredient = getIngredient(sandwich, index);
			if(ingredient == null) {
				compoundTag.putString("Ingredient" + index, Registry.ITEM.getId(item).toString());
				return;
			}
		}
	}

	public static void addIngredient(ItemStack sandwich, ItemStack itemStack) {
		addIngredient(sandwich, itemStack.getItem());
	}

	@Nullable
	private static Item getIngredient(ItemStack sandwich, int index) {
		CompoundTag compoundTag = sandwich.getTag();
		if(compoundTag != null) {
			if(compoundTag.contains("Ingredient" + index)) {
				Item ingredient = Registry.ITEM.get(new Identifier(compoundTag.getString("Ingredient" + index)));
				if(ingredient.isFood()) {
					return Registry.ITEM.get(new Identifier(compoundTag.getString("Ingredient" + index)));
				}
			}
		}
		return null;
	}

	private static List<Pair<Item, Integer>> getIngredients(ItemStack sandwich) {
		List<Pair<Item, Integer>> ingredientList = new ArrayList<>();
		for(int i = 1; i <= 3; i++) {
			Item ingredient = getIngredient(sandwich, i);
			if(ingredient != null) {
				ingredientList.add(Pair.of(ingredient, i));
			}
		}
		return ingredientList;
	}

	private static List<Pair<Integer, Integer>> getComplements(ItemStack sandwich) {
		List<Pair<Integer, Integer>> complements = new ArrayList<>();
		List<Pair<Item, Integer>> ingredientList = getIngredients(sandwich);
		for(Pair pair : getComplementsList()) {
			if(ingredientList.size() >= 2) {
				if(pair.equals(Pair.of(ingredientList.get(0).getFirst(), ingredientList.get(1).getFirst())) || pair.equals(Pair.of(ingredientList.get(1).getFirst(), ingredientList.get(0).getFirst()))) {
					complements.add(Pair.of(1, 2));
				}
			}
			if(ingredientList.size() == 3) {
				if(pair.equals(Pair.of(ingredientList.get(0).getFirst(), ingredientList.get(2).getFirst())) || pair.equals(Pair.of(ingredientList.get(2).getFirst(), ingredientList.get(0).getFirst()))) {
					complements.add(Pair.of(1, 3));
				}
				if(pair.equals(Pair.of(ingredientList.get(1).getFirst(), ingredientList.get(2).getFirst())) || pair.equals(Pair.of(ingredientList.get(2).getFirst(), ingredientList.get(1).getFirst()))) {
					complements.add(Pair.of(2, 3));
				}
			}
		}
		return complements;
	}

	private static List<Integer> getNormalIngredients(ItemStack sandwich) {
		List<Integer> normalIngredients = new ArrayList<>();
		for(Pair<Item, Integer> ingredientEntry : getIngredients(sandwich)) {
			normalIngredients.add(ingredientEntry.getSecond());
		}
		for(Pair<Integer, Integer> pair : getComplements(sandwich)) {
			normalIngredients.remove(pair.getFirst());
			normalIngredients.remove(pair.getSecond());
		}
		return normalIngredients;
	}

	private static int getProvidedHunger(ItemStack sandwich) {
		int hunger = sandwich.getItem().getFoodComponent().getHunger();
		for(Pair<Integer, Integer> indexes : getComplements(sandwich)) {
			hunger = hunger + (int) ((Math.ceil(getIngredient(sandwich, indexes.getFirst()).getFoodComponent().getHunger() / 3) + Math.ceil(getIngredient(sandwich, indexes.getSecond()).getFoodComponent().getHunger() / 3)) * 2);
		}
		for(Integer index : getNormalIngredients(sandwich)) {
			hunger = hunger + (int) Math.ceil(getIngredient(sandwich, index).getFoodComponent().getHunger() / 3);
		}
		return hunger;
	}

	private static float getProvidedSaturation(ItemStack sandwich) {
		float saturation = sandwich.getItem().getFoodComponent().getSaturationModifier();
		for(Pair<Integer, Integer> indexes : getComplements(sandwich)) {
			saturation = +(float) ((Math.ceil(getIngredient(sandwich, indexes.getFirst()).getFoodComponent().getSaturationModifier() / 3) + Math.ceil(getIngredient(sandwich, indexes.getSecond()).getFoodComponent()
																																																  .getSaturationModifier() / 3)) * 2);
		}
		for(Integer index : getNormalIngredients(sandwich)) {
			saturation = +(float) Math.ceil(getIngredient(sandwich, index).getFoodComponent().getSaturationModifier() / 3);
		}
		return saturation;
	}

	@Override
	public ItemStack finishUsing(ItemStack sandwich, World world, LivingEntity user) {
		if(user instanceof PlayerEntity) {
			int providedHunger = getProvidedHunger(sandwich);
			float providedSaturation = getProvidedSaturation(sandwich);
			PlayerEntity player = (PlayerEntity) user;
			player.getHungerManager().add(providedHunger, providedSaturation);
			for(Pair<Item, Integer> ingredientEntry : getIngredients(sandwich)) {
				List<Pair<StatusEffectInstance, Float>> statusEffectList = ingredientEntry.getFirst().getFoodComponent().getStatusEffects();
				Iterator statusEffects = statusEffectList.iterator();
				while(statusEffects.hasNext()) {
					Pair<StatusEffectInstance, Float> pair = (Pair) statusEffects.next();
					if(!world.isClient && pair.getFirst() != null && world.random.nextFloat() < pair.getSecond()) {
						player.addStatusEffect(new StatusEffectInstance(pair.getFirst()));
					}
				}
			}
		}
		return super.finishUsing(sandwich, world, user);
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void appendTooltip(ItemStack sandwich, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		for(Pair<Integer, Integer> indexes : getComplements(sandwich)) {
			tooltip.add((new LiteralText("- ")).append(new TranslatableText(getIngredient(sandwich, indexes.getFirst()).getTranslationKey())).formatted(Formatting.GREEN));
			tooltip.add((new LiteralText("- ")).append(new TranslatableText(getIngredient(sandwich, indexes.getSecond()).getTranslationKey())).formatted(Formatting.GREEN));
		}
		for(Integer index : getNormalIngredients(sandwich)) {
			tooltip.add((new LiteralText("- ")).append(new TranslatableText(getIngredient(sandwich, index).getTranslationKey())).formatted(Formatting.GRAY));
		}
	}

	@Override
	public boolean hasGlint(ItemStack sandwich) {
		List<Pair<Item, Integer>> ingredientList = getIngredients(sandwich);
		if(!ingredientList.isEmpty()) {
			for(Pair<Item, Integer> ingredientEntry : ingredientList) {
				Item ingredient = ingredientEntry.getFirst();
				if(ingredient.hasGlint(new ItemStack(ingredient))) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<Pair<Item, Item>> getComplementsList() {
		List<Pair<Item, Item>> list = new ArrayList<>();
		list.add(Pair.of(Items.APPLE, CEFItems.CHOCOLATE));
		list.add(Pair.of(Items.CHICKEN, Items.HONEY_BOTTLE));
		list.add(Pair.of(Items.COOKED_BEEF, CEFItems.CHEESE));
		list.add(Pair.of(Items.ENCHANTED_GOLDEN_APPLE, Items.BEETROOT));
		list.add(Pair.of(Items.GOLDEN_APPLE, Items.DRIED_KELP));
		list.add(Pair.of(CEFItems.MARSHMALLOW, CEFItems.CHOCOLATE));
		list.add(Pair.of(CEFItems.MARSHMALLOW, Items.HONEY_BOTTLE));
		list.add(Pair.of(Items.RABBIT, Items.BEETROOT));
		list.add(Pair.of(Items.SPIDER_EYE, CEFItems.CHOCOLATE));
		list.add(Pair.of(CEFItems.TOMATO, CEFItems.CHEESE));
		list.add(Pair.of(CEFItems.TOMATO, CEFItems.LETTUCE));
		return list;
	}
}
