package com.lyeeedar.Map.Generators

import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.ObjectSet
import com.lyeeedar.Board.Level
import com.lyeeedar.Board.LevelTheme
import com.lyeeedar.Direction
import com.lyeeedar.Map.DungeonMap
import com.lyeeedar.Map.DungeonMapEntry
import com.lyeeedar.Map.Objective.AbstractObjective
import com.lyeeedar.Util.Point
import com.lyeeedar.Util.Random
import com.lyeeedar.Util.random
import com.lyeeedar.Util.removeRandom
import ktx.collections.set

/**
 * Created by Philip on 24-Jul-16.
 */

class HubGenerator(val seed: Long)
{
	val ran = Random.obtainTS(seed)
	val maxCorridorLength = 4
	val maxLength = 10
	var numRoomsToSpawn = 0

	fun generate(theme: LevelTheme, numRooms: Int, depth: Int, objective: AbstractObjective, dungeonName: String): DungeonMap
	{
		numRoomsToSpawn = numRooms
		val map = DungeonMap(seed, numRooms, depth, theme, objective, dungeonName)

		val hub = DungeonMapEntry(Point.ZERO.copy())
		hub.isRoom = true
		map.map[hub.point.hashCode()] = hub

		for (dir in Direction.CardinalValues)
		{
			val placed = expand(map, Point(dir.x, dir.y), dir)
			if (placed)
			{
				hub.connections[dir] = map.map[(Point.ZERO + dir).hashCode()]
			}
		}

		val endOfChainRooms = Array<DungeonMapEntry>(false, 16)
		val unfilledRooms = Array<DungeonMapEntry>(false, 16)

		for (room in map.map.values().sortedBy { it.point.hashCode() })
		{
			if (room.isRoom && room != hub)
			{
				if (room.connections.size == 1)
				{
					endOfChainRooms.add(room)
				}
				else
				{
					unfilledRooms.add(room)
				}
			}
		}

		val requiredLevels = objective.getRequiredLevels()
		for (level in requiredLevels)
		{
			val arrToUse = if (endOfChainRooms.size > 0) endOfChainRooms else unfilledRooms
			val room = arrToUse.removeRandom(ran)
			room.level = level.copy()
			room.isRoom = true
		}

		// 0.1 empty
		// 0.7 bad
		// 0.2 good
		val totalCount = endOfChainRooms.size + unfilledRooms.size

		val empty = (unfilledRooms.size * 0.1).toInt()
		val good = Math.max(0, (totalCount * 0.2).toInt() - endOfChainRooms.size)
		val bad = unfilledRooms.size - (empty + good)

		val emptyRooms = Array<DungeonMapEntry>(false, 16)
		val goodRooms = Array<DungeonMapEntry>(false, 16)
		val badRooms = Array<DungeonMapEntry>(false, 16)

		for (room in endOfChainRooms)
		{
			room.type = DungeonMapEntry.Type.GOOD
			goodRooms.add(room)
		}

		for (i in 1..good)
		{
			val room = unfilledRooms.removeRandom(ran)
			room.type = DungeonMapEntry.Type.GOOD
			goodRooms.add(room)
		}
		for (i in 1..bad)
		{
			val room = unfilledRooms.removeRandom(ran)
			room.type = DungeonMapEntry.Type.BAD
			badRooms.add(room)
		}
		for (i in 1..empty)
		{
			val room = unfilledRooms.removeRandom(ran)
			room.type = DungeonMapEntry.Type.EMPTY
			emptyRooms.add(room)
		}

		val levels = Level.loadAll(theme.name)

		fun getWeightedTypes(count: Int, type: DungeonMapEntry.Type): Array<String>
		{
			val typeList = Array<String>()

			val weights = theme.roomWeights[type] ?: return typeList
			val total = weights.sumBy { it.value }.toDouble()
			if (total == 0.0) return typeList

			for (weight in weights)
			{
				val ratio = weight.value.toDouble() / total
				val num = (count * ratio).toInt()
				for (i in 1..num) typeList.add(weight.key)
			}

			return typeList
		}

		val typeMap = ObjectMap<DungeonMapEntry.Type, Array<String>>()
		for (type in DungeonMapEntry.Type.values())
		{
			val count = when (type)
			{
				DungeonMapEntry.Type.GOOD -> goodRooms.size
				DungeonMapEntry.Type.BAD -> badRooms.size
				DungeonMapEntry.Type.EMPTY -> emptyRooms.size
				else -> 0
			}
			typeMap[type] = getWeightedTypes(count, type)
		}

		fun assignLevels(type: DungeonMapEntry.Type, rooms: Array<DungeonMapEntry>)
		{
			val used = ObjectSet<Level>()
			val typeList = typeMap[type]

			for (room in rooms)
			{
				if (typeList.size == 0) return
				val chosenType = typeList.removeRandom(ran)

				// build list of valid levels
				val valid = Array<Level>()
				for (level in levels[type])
				{
					if (depth < level.minDepth || depth > level.maxDepth || level.type != chosenType) continue

					if (used.contains(level)) continue

					for (i in 0..level.rarity.ordinal) valid.add(level)
				}

				if (valid.size > 0)
				{
					val level = valid.random(ran)
					room.level = level.copy()

					println("Spawning level: " + level.loadPath)

					used.add(level)
				}
				else
				{
					room.isRoom = false
				}
			}
		}

		assignLevels(DungeonMapEntry.Type.EMPTY, emptyRooms)
		assignLevels(DungeonMapEntry.Type.GOOD, goodRooms)
		assignLevels(DungeonMapEntry.Type.BAD, badRooms)

		map.finishSetup()
		map.objective.update(map)

		return map
	}

	fun expand(map: DungeonMap, point: Point, dir: Direction, corridorCount: Int = 0, depth: Int = 0): Boolean
	{
		if (numRoomsToSpawn <= 0) return false
		if (!map.isFree(point)) return false

		val freeDirections = Array<Direction>()
		if (map.isFree(point + dir)) { freeDirections.add(dir); freeDirections.add(dir); freeDirections.add(dir) } // weight going forward more
		if (map.isFree(point + dir.cardinalClockwise)) freeDirections.add(dir.cardinalClockwise)
		if (map.isFree(point + dir.cardinalAnticlockwise)) freeDirections.add(dir.cardinalAnticlockwise)

		var makeRoom: Boolean

		if (freeDirections.size == 0)
		{
			if (corridorCount == 0) return false
			makeRoom = true
		}
		else if (corridorCount > 0)
		{
			// make a room, higher prob with more corridor
			makeRoom = ran.nextInt(maxCorridorLength) < corridorCount
		}
		else
		{
			makeRoom = false
		}

		val tempRoom = DungeonMapEntry(point)
		tempRoom.connections[dir.opposite] = map.map[(point + dir.opposite).hashCode()]
		map.map[point.hashCode()] = tempRoom

		if (!makeRoom)
		{
			// attempt to make a corridor
			val newDir = freeDirections.random(ran)
			val placed = expand(map, point + newDir, newDir, corridorCount+1, depth+1)
			if (!placed)
			{
				makeRoom = true
			}
			else
			{
				tempRoom.connections[newDir] = map.map[(point + newDir).hashCode()]
			}
		}

		if (makeRoom)
		{
			tempRoom.isRoom = true
			numRoomsToSpawn--

			if (freeDirections.size == 0)
			{
				tempRoom.type = DungeonMapEntry.Type.GOOD
			}
			else
			{
				// assign random type
				tempRoom.type = DungeonMapEntry.Type.values().asSequence().random(ran)!!

				if (depth < maxLength)
				{
					val numRooms = sequenceOf(0, 1, 1, 1, 1, 1, 2, 2, 3).random(ran)!!

					for (i in 0..numRooms-1)
					{
						val newDir = freeDirections.removeRandom(ran)

						val placed = expand(map, point + newDir, newDir, 0, depth+1)
						if (placed)
						{
							tempRoom.connections[newDir] = map.map[(point + newDir).hashCode()]
						}

						if (freeDirections.size == 0) break
					}
				}
			}
		}

		return true
	}
}