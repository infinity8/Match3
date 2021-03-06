package com.lyeeedar.Player

import com.badlogic.gdx.utils.ObjectMap
import com.lyeeedar.Player.Ability.Ability
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.UI.MessageBox
import com.lyeeedar.UI.UnlockTree
import com.lyeeedar.Util.AssetManager
import ktx.collections.get
import ktx.collections.set

/**
 * Created by Philip on 06-Aug-16.
 */

class PlayerData
{
	val unlockedSprites = com.badlogic.gdx.utils.Array<Sprite>()
	lateinit var chosenSprite: Sprite

	var maxhp: Int = 10
	var attackDam: Int = 1
	var abilityDam: Int = 3
	var powerGain: Int = 0

	val abilities = Array<String?>(4){e -> null}
	var gold = 200
	val inventory = ObjectMap<String, Item>()

	val skillTrees = ObjectMap<String, UnlockTree<Ability>>()

	init
	{
		unlockedSprites.add(AssetManager.loadSprite("Oryx/Custom/heroes/farmer_m", drawActualSize = true))
		unlockedSprites.add(AssetManager.loadSprite("Oryx/Custom/heroes/farmer_f", drawActualSize = true))

		chosenSprite = unlockedSprites[0]

		abilities[0] = "Firebolt"
		getSkillTree("UnlockTrees/Fire")
		getSkillTree("UnlockTrees/Warrior")
	}

	fun getSkillTree(path: String): UnlockTree<Ability>
	{
		if (skillTrees.containsKey(path)) return skillTrees[path]
		else
		{
			val tree = UnlockTree.load(path, {Ability()})
			skillTrees[path] = tree
			return tree
		}
	}

	fun getAbility(name: String): Ability?
	{
		for (tree in skillTrees)
		{
			val skill = tree.value.boughtDescendants()[name]
			if (skill != null) return skill
		}

		return null
	}

	fun addItem(item: Item)
	{
		val existing = inventory[item.name]

		if (existing != null)
		{
			existing.count += item.count
		}
		else
		{
			inventory[item.name] = item
		}
	}

	fun mergePlayerDataBack(player: Player)
	{
		var message = "Quest rewards:\n\n"

		gold += player.gold
		message += "Gold: +${player.gold}\n"

		for (item in player.inventory)
		{
			addItem(item.value)

			message += "${item.value.name}: +${item.value.count}\n"
		}

		MessageBox("Quest Complete", message, Pair("Okay", {}))
	}
}