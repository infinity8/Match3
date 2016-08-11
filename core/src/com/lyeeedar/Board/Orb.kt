package com.lyeeedar.Board

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Direction
import com.lyeeedar.Sprite.Sprite
import com.lyeeedar.Sprite.SpriteAnimation.BlinkAnimation
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.FastEnumMap
import com.lyeeedar.Util.Point
import com.lyeeedar.Util.tryGet

/**
 * Created by Philip on 04-Jul-16.
 */

class Orb(val desc: OrbDesc, val theme: LevelTheme): Point()
{
	var armed: ((point: Point, grid: Grid) -> Unit)? = null

	var special: Special? = null
		set(value)
		{
			if (sinkable) return

			field = value

			if (value != null)
			{
				val nsprite = value.sprite.copy()
				nsprite.colour = sprite.colour
				if (nsprite.colourAnimation == null) nsprite.colourAnimation = BlinkAnimation.obtain().set(nsprite.colour, 0.1f, 2.5f, false)

				sprite = nsprite
			}
		}

	var markedForDeletion: Boolean = false
	var deletionEffectDelay: Float = 0f
	var skipPowerOrb = false

	var sealSprite: Sprite = theme.sealSprites.tryGet(0).copy()
	val sealBreak = AssetManager.loadSprite("EffectSprites/Aegis/Aegis", 0.1f, Color(0.2f, 0f, 0.2f, 1f), Sprite.AnimationMode.TEXTURE, null, false, true)
	var sealCount = 0
		set(value)
		{
			field = value
			sealSprite = theme.sealSprites.tryGet(sealCount-1).copy()
		}
	val sealed: Boolean
		get() = sealCount > 0

	var hasAttack: Boolean = false
		set(value)
		{
			if (!field && value)
			{
				field = value
				val nsprite = AssetManager.loadSprite("Oryx/uf_split/uf_items/skull_small", drawActualSize = true)
				nsprite.colour = sprite.colour
				sprite = nsprite
			}
		}

	var attackTimer = 0

	val sinkable: Boolean
		get() = desc.sinkable

	val key: Int
		get() = desc.key

	var sprite: Sprite = desc.sprite.copy()

	val movePoints = Array<Point>()
	var spawnCount = -1
	var cascadeCount = 0

	override fun toString(): String
	{
		return desc.key.toString()
	}

	fun setAttributes(orb: Orb)
	{
		sealCount = orb.sealCount
		hasAttack = orb.hasAttack
		attackTimer = orb.attackTimer
		special = orb.special
	}

	companion object
	{
		// ----------------------------------------------------------------------
		val validOrbs: Array<OrbDesc> = Array()

		fun getOrb(key: Int) = validOrbs.first { it.key == key }
		fun getOrb(name: String) = validOrbs.first { it.name == name }

		init
		{
			loadOrbs()
		}

		fun loadOrbs()
		{
			validOrbs.clear()

			val xml = XmlReader().parse(Gdx.files.internal("Orbs/Orbs.xml"))

			val template = xml.getChildByName("Template")
			val baseSprite = AssetManager.loadSprite(template.getChildByName("Sprite"))
			val deathSprite = AssetManager.loadSprite(template.getChildByName("Death"))

			val types = xml.getChildByName("Types")
			for (i in 0..types.childCount-1)
			{
				val type = types.getChild(i)
				val name = type.name
				val colour = AssetManager.loadColour(type.getChildByName("Colour"))

				val orbDesc = OrbDesc()
				orbDesc.sprite = baseSprite.copy()
				orbDesc.sprite.colour = colour
				orbDesc.name = name

				orbDesc.death = deathSprite
				orbDesc.death.colour = colour

				validOrbs.add(orbDesc)
			}
		}
	}
}

class OrbDesc()
{
	constructor(sprite: Sprite, death: Sprite, sinkable: Boolean, key: Int, name: String) : this()
	{
		this.sprite = sprite
		this.death = death
		this.sinkable = sinkable
		this.name = name
		this.key = key
	}

	lateinit var sprite: Sprite
	lateinit var death: Sprite
	var sinkable = false
	var key: Int = -1
	var name: String = ""
		set(value)
		{
			field = value
			key = value.hashCode()
		}
}