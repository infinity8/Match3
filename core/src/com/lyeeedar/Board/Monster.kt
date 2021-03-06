package com.lyeeedar.Board

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Direction
import com.lyeeedar.Player.Ability.Permuter
import com.lyeeedar.Player.Ability.Targetter
import com.lyeeedar.Renderables.Animation.BumpAnimation
import com.lyeeedar.Renderables.Animation.ExpandAnimation
import com.lyeeedar.Renderables.Animation.LeapAnimation
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Util.*
import ktx.collections.set
import ktx.collections.toGdxArray

/**
 * Created by Philip on 22-Jul-16.
 */

class Monster(val desc: MonsterDesc) : Creature(desc.hp, desc.size, desc.sprite.copy(), desc.death.copy())
{
	var isSummon = false

	var attackSpeed: Int = 5
	var attackDelay: Int = 5
	var attackAccumulator: Int = 1

	val rewards = ObjectMap<String, Pair<Int, Int>>()

	val abilities = Array<MonsterAbility>()

	init
	{
		attackSpeed = desc.attackSpeed
		attackDelay = desc.attackDelay
		abilities.addAll(desc.abilities.map{ it.copy() }.toGdxArray())

		attackAccumulator = (MathUtils.random() * attackDelay).toInt()

		for (reward in desc.rewards)
		{
			rewards[reward.key] = reward.value
		}
	}

	override fun onTurn(grid: Grid)
	{
		attackAccumulator++
		if (attackAccumulator >= attackDelay)
		{
			attackAccumulator -= attackDelay

			// do attack
			val tile = grid.grid.filter { validAttack(grid, it) }.random()

			if (tile != null)
			{
				tile.orb!!.attackTimer = attackSpeed
				val diff = tile.getPosDiff(tiles[0, 0])
				diff[0].y *= -1
				sprite.animation = BumpAnimation.obtain().set(0.2f, diff)

				val dst = tile.euclideanDist(tiles[0, 0])
				val animDuration = 0.25f + tile.euclideanDist(tiles[0, 0]) * 0.025f
				val attackSprite = AssetManager.loadSprite("Oryx/uf_split/uf_items/skull_small", drawActualSize = true)
				attackSprite.colour = tile.orb!!.sprite.colour
				attackSprite.animation = LeapAnimation.obtain().set(animDuration, tile.getPosDiff(tiles[0, 0]), 1f + dst * 0.25f)
				attackSprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
				tile.effects.add(attackSprite)

				tile.orb!!.delayDisplayAttack = animDuration
			}
		}

		var doneAbility = false
		for (ability in abilities)
		{
			ability.cooldownTimer--
			if (ability.cooldownTimer <= 0 && !doneAbility)
			{
				if (MathUtils.randomBoolean())
				{
					ability.cooldownTimer = ability.cooldownMin + MathUtils.random(ability.cooldownMax - ability.cooldownMin)
					ability.activate(grid, this)

					doneAbility = true // only use 1 a turn
				}
			}
		}

		if (isSummon)
		{
			hp--
		}
	}
}

fun validAttack(grid: Grid, tile: Tile): Boolean
{
	if (tile.orb == null) return false
	if (tile.orb!!.special != null) return false
	if (tile.orb!!.hasAttack) return false

	for (dir in Direction.CardinalValues)
	{
		val ntile = grid.tile(tile + dir) ?: return false
		if (!ntile.canHaveOrb) return false
	}

	return true
}

class MonsterAbility
{
	enum class Target
	{
		NEIGHBOUR,
		RANDOM
	}

	enum class Effect
	{
		ATTACK,
		SEALEDATTACK,
		SHIELD,
		SEAL,
		BLOCK,
		MOVE,
		HEAL,
		SUMMON
	}

	var cooldownTimer: Int = 0
	var cooldownMin: Int = 1
	var cooldownMax: Int = 1
	lateinit var target: Target
	lateinit var targetRestriction: Targetter
	var targetCount: Int = 1
	lateinit var permuter: Permuter
	lateinit var effect: Effect
	val data = ObjectMap<String, String>()

	fun copy(): MonsterAbility
	{
		val ability = MonsterAbility()
		ability.cooldownTimer = cooldownTimer
		ability.cooldownMin = cooldownMin
		ability.cooldownMax = cooldownMax
		ability.target = target
		ability.targetRestriction = targetRestriction
		ability.targetCount = targetCount
		ability.permuter = permuter
		ability.effect = effect
		ability.data.putAll(data)

		return ability
	}

	fun activate(grid: Grid, monster: Monster)
	{
		println("Monster trying to use ability '$effect'")

		if (effect == Effect.HEAL)
		{
			monster.hp += data["AMOUNT"].toInt()
			if (monster.hp > monster.maxhp) monster.hp = monster.maxhp

			val sprite = AssetManager.loadSprite("EffectSprites/Heal/Heal", 0.1f, Colour(0f,1f,0f,1f))
			sprite.size[0] = monster.size
			sprite.size[1] = monster.size

			monster.tiles[0, 0].effects.add(sprite)

			return
		}

		val availableTargets = Array<Tile>()

		if (target == Target.NEIGHBOUR)
		{
			availableTargets.addAll(monster.getBorderTiles(grid, data["RANGE", "1"].toInt()))
		}
		else if (target == Target.RANDOM)
		{
			availableTargets.addAll(grid.grid)
		}
		else
		{
			throw NotImplementedError()
		}

		var validTargets = availableTargets.filter { targetRestriction.isValid(it, data) }

		if (targetRestriction.type == Targetter.Type.ORB && (effect == Effect.ATTACK || effect == Effect.SEALEDATTACK))
		{
			validTargets = validTargets.filter { validAttack(grid, it) }
		}

		val chosen = validTargets.asSequence().random(targetCount)

		val finalTargets = Array<Tile>()

		for (target in chosen)
		{
			for (t in permuter.permute(target, grid, data))
			{
				if (!finalTargets.contains(t, true))
				{
					finalTargets.add(t)
				}
			}
		}

		if (effect == Effect.MOVE)
		{
			fun isValid(t: Tile): Boolean
			{
				for (x in 0..monster.size-1)
				{
					for (y in 0..monster.size-1)
					{
						val tile = grid.tile(t.x + x, t.y + y) ?: return false

						if (tile.orb != tile.contents && tile.monster != monster)
						{
							return false
						}

						if (!tile.canHaveOrb)
						{
							return false
						}

						if (tile.orb == null)
						{
							return false
						}

						if (tile.orb!!.special != null)
						{
							return false
						}
					}
				}

				return true
			}

			val target = validTargets.filter(::isValid).asSequence().random()

			if (target != null)
			{
				val start = monster.tiles.first()
				monster.setTile(target, grid)
				val end = monster.tiles.first()

				if (this.target == Target.RANDOM)
				{
					val dst = target.euclideanDist(monster.tiles[0, 0])
					val animDuration = 0.25f + dst * 0.025f

					monster.sprite.animation = LeapAnimation.obtain().set(animDuration, target.getPosDiff(monster.tiles[0, 0]), 1f + dst * 0.25f)
					monster.sprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
				}
				else
				{
					monster.sprite.animation = MoveAnimation.obtain().set(0.25f, UnsmoothedPath(end.getPosDiff(start)), Interpolation.linear)
				}
			}

			return
		}

		for (target in finalTargets)
		{
			val strength = data.get("STRENGTH", "1").toInt()

			if (effect == Effect.ATTACK || effect == Effect.SEALEDATTACK)
			{
				val speed = data.get("SPEED", monster.attackSpeed.toString()).toInt()

				if (target.orb == null)
				{
					target.effects.add(grid.hitEffect.copy())
					target.orb = Orb(Orb.getRandomOrb(grid.level), grid.level.theme)
				}

				target.orb!!.attackTimer = speed
				val diff = target.getPosDiff(monster.tiles[0, 0])
				diff[0].y *= -1
				monster.sprite.animation = BumpAnimation.obtain().set(0.2f, diff)

				val dst = target.euclideanDist(monster.tiles[0, 0])
				val animDuration = 0.25f + dst * 0.025f
				val attackSprite = AssetManager.loadSprite("Oryx/uf_split/uf_items/skull_small", drawActualSize = true)
				attackSprite.colour = target.orb!!.sprite.colour
				attackSprite.animation = LeapAnimation.obtain().set(animDuration, target.getPosDiff(monster.tiles[0, 0]), 1f + dst * 0.25f)
				attackSprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
				target.effects.add(attackSprite)

				target.orb!!.delayDisplayAttack = animDuration
			}
			if (effect == Effect.SHIELD)
			{
				target.shield = Shield(grid.level.theme)
				target.shield!!.count = strength
			}
			if (effect == Effect.SEAL || effect == Effect.SEALEDATTACK)
			{
				target.swappable?.sealCount = strength
			}
			if (effect == Effect.MOVE)
			{

			}
			if (effect == Effect.BLOCK)
			{
				target.block = Block(grid.level.theme)
				target.block!!.count = strength
			}
			if (effect == Effect.SUMMON)
			{
				val factionName = data["FACTION"]
				val name = data["NAME"]

				val faction = Faction.load(factionName)
				val summoned = Monster(faction.get(name)!!)
				summoned.isSummon = true

				summoned.setTile(target, grid)
			}
		}
	}

	companion object
	{
		fun load(xml: XmlReader.Element) : MonsterAbility
		{
			val ability = MonsterAbility()

			val cooldown = xml.get("Cooldown").split(",")
			ability.cooldownMin = cooldown[0].toInt()
			ability.cooldownMax = cooldown[1].toInt()
			ability.cooldownTimer = ability.cooldownMin + MathUtils.random(ability.cooldownMax - ability.cooldownMin)

			ability.target = Target.valueOf(xml.get("Target", "NEIGHBOUR").toUpperCase())
			ability.targetCount = xml.getInt("Count", 1)

			ability.targetRestriction = Targetter(Targetter.Type.valueOf(xml.get("TargetRestriction", "Orb").toUpperCase()))
			ability.permuter = Permuter(Permuter.Type.valueOf(xml.get("Permuter", "Single").toUpperCase()))
			ability.effect = Effect.valueOf(xml.get("Effect", "Attack").toUpperCase())

			val dEl = xml.getChildByName("Data")
			if (dEl != null)
			{
				for (i in 0..dEl.childCount-1)
				{
					val el = dEl.getChild(i)
					ability.data[el.name.toUpperCase()] = el.text.toUpperCase()
				}
			}

			return ability
		}
	}
}