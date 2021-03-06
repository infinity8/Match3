package com.lyeeedar.Board

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Map.DungeonMapEntry
import com.lyeeedar.Renderables.Sprite.DirectedSprite
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Renderables.Sprite.SpriteWrapper
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.FastEnumMap
import ktx.collections.get
import ktx.collections.set
import com.lyeeedar.Util.iterator

/**
 * Created by Philip on 13-Jul-16.
 */

class LevelTheme
{
	lateinit var name: String

	lateinit var floor: SpriteWrapper
	lateinit var wall: SpriteWrapper
	lateinit var pit: SpriteWrapper

	lateinit var chestFull: Sprite
	lateinit var chestEmpty: Sprite
	lateinit var coin: Sprite
	lateinit var plate: Sprite
	val blockSprites = Array<Sprite>()
	val sealSprites = Array<Sprite>()
	val shieldSprites = Array<Sprite>()

	lateinit var mapRoom: DirectedSprite
	lateinit var mapCorridor: DirectedSprite

	val roomWeights = FastEnumMap<DungeonMapEntry.Type, ObjectMap<String, Int>>(DungeonMapEntry.Type::class.java)
	val spawnList = Array<String>()

	companion object
	{
		fun load(path: String): LevelTheme
		{
			val xml = XmlReader().parse(Gdx.files.internal("Themes/$path.xml"))

			val theme = LevelTheme()
			theme.name = path

			theme.floor = SpriteWrapper.load(xml.getChildByName("Floor"))
			theme.wall = SpriteWrapper.load(xml.getChildByName("Wall"))
			theme.pit = SpriteWrapper.load(xml.getChildByName("Pit"))

			val chestEl = xml.getChildByName("Chest")
			theme.chestFull = AssetManager.loadSprite(chestEl.getChildByName("Full"))
			theme.chestEmpty = AssetManager.loadSprite(chestEl.getChildByName("Empty"))
			theme.coin = AssetManager.loadSprite(xml.getChildByName("Coin"))
			theme.plate = AssetManager.loadSprite(xml.getChildByName("Plate"))

			val blockEls = xml.getChildByName("Block")
			for (i in 0..blockEls.childCount-1)
			{
				theme.blockSprites.add(AssetManager.loadSprite(blockEls.getChild(i)))
			}

			val sealEls = xml.getChildByName("Seal")
			for (i in 0..sealEls.childCount-1)
			{
				theme.sealSprites.add(AssetManager.loadSprite(sealEls.getChild(i)))
			}

			val shieldEls = xml.getChildByName("Shield")
			for (i in 0..shieldEls.childCount-1)
			{
				theme.shieldSprites.add(AssetManager.loadSprite(shieldEls.getChild(i)))
			}

			val mapEl = xml.getChildByName("Map")
			theme.mapRoom = DirectedSprite.load(mapEl.getChildByName("Room"))
			theme.mapCorridor = DirectedSprite.load(mapEl.getChildByName("Corridor"))

			val weightsEl = xml.getChildByName("RoomWeights")
			for (i in 0..weightsEl.childCount-1)
			{
				val typeEl = weightsEl.getChild(i)
				val type = DungeonMapEntry.Type.valueOf(typeEl.name.toUpperCase())

				theme.roomWeights[type] = ObjectMap()
				val map = theme.roomWeights[type]

				for (ii in 0..typeEl.childCount-1)
				{
					val el = typeEl.getChild(ii)
					val split = el.text.split(",")
					map[split[0]] = split[1].toInt()
				}
			}

			val spawnsEl = xml.getChildByName("SpawnWeights")
			if (spawnsEl != null)
			{
				for (el in spawnsEl)
				{
					val split = el.text.split(",")

					for (i in 0..split[1].toInt()-1)
					{
						theme.spawnList.add(split[0])
					}
				}
			}
			else
			{
				theme.spawnList.add("Orb")
			}

			return theme
		}
	}
}