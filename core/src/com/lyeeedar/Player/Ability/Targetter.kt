package com.lyeeedar.Player.Ability

import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.XmlReader
import com.badlogic.gdx.utils.reflect.ClassReflection
import com.lyeeedar.Board.Grid
import com.lyeeedar.Board.Match5
import com.lyeeedar.Board.Tile

/**
 * Created by Philip on 21-Jul-16.
 */

class Targetter(val type: Type)
{
	enum class Type
	{
		BASICORB,
		ORB,
		SPECIAL,
		BLOCK,
		EMPTY,
		SEALED,
		MONSTER,
		ATTACK,
		TILE,
		SHIELD
	}

	lateinit var isValid: (tile: Tile, data: ObjectMap<String, String>) -> Boolean

	init
	{
		isValid = when(type)
		{
			Type.BASICORB -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.orb != null && !tile.orb!!.hasAttack && tile.orb!!.special == null && !tile.orb!!.sealed
			Type.ORB -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.orb != null && !tile.orb!!.hasAttack && tile.orb?.special !is Match5
			Type.SPECIAL  -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.orb?.special != null
			Type.BLOCK -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.block != null
			Type.EMPTY -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.contents == null && tile.canHaveOrb
			Type.SEALED -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.swappable?.sealed ?: false
			Type.MONSTER ->  fun (tile: Tile, data: ObjectMap<String, String>) = tile.monster != null
			Type.ATTACK ->  fun (tile: Tile, data: ObjectMap<String, String>) = tile.orb?.hasAttack ?: false
			Type.TILE -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.canHaveOrb
			Type.SHIELD -> fun (tile: Tile, data: ObjectMap<String, String>) = tile.shield != null
			else -> throw Exception("Invalid targetter type $type")
		}
	}
}