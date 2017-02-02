package com.lyeeedar.Board

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.ObjectSet
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Direction
import com.lyeeedar.Player.Ability.Effect
import com.lyeeedar.Player.Ability.Permuter
import com.lyeeedar.Player.Ability.Targetter
import com.lyeeedar.Renderables.Animation.*
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Util.*
import ktx.collections.get
import ktx.collections.set
import ktx.collections.toGdxArray

/**
 * Created by Philip on 22-Jul-16.
 */

class Monster(val desc: MonsterDesc)
{
	var hp: Int = 1
		set(value)
		{
			if (value < field)
			{
				sprite.colourAnimation = BlinkAnimation.obtain().set(Colour(Color.RED), sprite.colour, 0.15f, true)
			}

			field = value
			if (field < 0) field = 0
		}

	var maxhp: Int = 1
		set(value)
		{
			field = value
			hp = value
		}

	var size = 2
		set(value)
		{
			field = value
			tiles = Array2D(size, size){ x, y -> Tile(0, 0) }
		}

	lateinit var tiles: Array2D<Tile>

	lateinit var sprite: Sprite
	lateinit var death: Sprite

	var attackSpeed: Int = 5
	var attackDelay: Int = 5
	var attackAccumulator: Int = 1

	val damSources = ObjectSet<Any>()

	val rewards = ObjectMap<String, Pair<Int, Int>>()

	val abilities = Array<MonsterAbility>()

	init
	{
		attackSpeed = desc.attackSpeed
		attackDelay = desc.attackDelay
		size = desc.size
		sprite = desc.sprite.copy()
		death = desc.death.copy()
		maxhp = desc.hp
		abilities.addAll(desc.abilities.map{ it.copy() }.toGdxArray())

		attackAccumulator = (MathUtils.random() * attackDelay).toInt()

		for (reward in desc.rewards)
		{
			rewards[reward.key] = reward.value
		}
	}

	fun onTurn(grid: Grid)
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
		HEAL
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
			for (tile in grid.grid)
			{
				val minDiff = monster.tiles.map { it.dist(tile) }.min()!!
				if (minDiff <= 1)
				{
					availableTargets.add(tile)
				}
			}
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

		if (effect == Effect.ATTACK || effect == Effect.SEALEDATTACK)
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
					}
				}

				return true
			}

			val target = validTargets.filter(::isValid).asSequence().random()

			if (target != null)
			{
				val start = monster.tiles.first()

				for (tile in monster.tiles)
				{
					tile.monster = null
				}
				for (x in 0..monster.size-1)
				{
					for (y in 0..monster.size - 1)
					{
						val tile = grid.tile(target.x + x, target.y + y)!!

						if (tile.orb != null)
						{
							val orb = tile.orb!!

							val sprite = orb.desc.death.copy()
							sprite.colour = orb.sprite.colour

							tile.effects.add(sprite)
						}

						tile.monster = monster
						monster.tiles[x, y] = tile
					}
				}

				val end = monster.tiles.first()

				monster.sprite.animation = MoveAnimation.obtain().set(0.25f, UnsmoothedPath(end.getPosDiff(start)), Interpolation.linear)
			}

			return
		}

		for (target in finalTargets)
		{
			val strength = data.get("STRENGTH", "1").toInt()

			if (effect == Effect.ATTACK || effect == Effect.SEALEDATTACK)
			{
				val speed = data.get("SPEED", monster.attackSpeed.toString()).toInt()

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