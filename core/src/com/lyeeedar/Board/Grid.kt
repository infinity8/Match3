package com.lyeeedar.Board

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.Actions.*
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectSet
import com.lyeeedar.Direction
import com.lyeeedar.Global
import com.lyeeedar.Player.Ability.Ability
import com.lyeeedar.Player.Item
import com.lyeeedar.Renderables.Animation.AlphaAnimation
import com.lyeeedar.Renderables.Animation.BumpAnimation
import com.lyeeedar.Renderables.Animation.ExpandAnimation
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Screens.GridScreen
import com.lyeeedar.UI.FullscreenMessage
import com.lyeeedar.UI.GridWidget
import com.lyeeedar.UI.PowerBar
import com.lyeeedar.UI.shake
import com.lyeeedar.Util.*
import ktx.actors.plus
import ktx.actors.then

/**
 * Created by Philip on 04-Jul-16.
 */

class Grid(val width: Int, val height: Int, val level: Level)
{
	val grid: Array2D<Tile> = Array2D(width, height ){ x, y -> Tile(x, y) }
	val spawnCount: Array2D<Int> = Array2D<Int>(width, height+1){ x, y -> 0 }

	val refillSprite = AssetManager.loadSprite("EffectSprites/Heal/Heal", 0.1f)

	// ----------------------------------------------------------------------
	var dragStart: Point = Point.MINUS_ONE
	var toSwap: Pair<Point, Point>? = null
	var lastSwapped: Point = Point.MINUS_ONE

	val animSpeed = 0.15f

	// ----------------------------------------------------------------------
	val onTurn = Event0Arg()
	val onTime = Event1Arg<Float>()
	val onPop = Event2Arg<Orb, Float>()
	val onSunk = Event1Arg<Sinkable>()
	val onDamaged = Event1Arg<Creature>()
	val onSpawn = Event1Arg<Swappable>()
	val onAttacked = Event1Arg<Orb>()

	// ----------------------------------------------------------------------
	var noMatchTimer = 0f
	var matchHint: Pair<Point, Point>? = null

	// ----------------------------------------------------------------------
	val hitEffect = AssetManager.loadParticleEffect("Hit")

	// ----------------------------------------------------------------------
	var activeAbility: Ability? = null
		set(value)
		{
			if (inTurn && value != null) return

			field = value

			if (value == null)
			{
				for (tile in grid)
				{
					tile.isSelected = false
				}
			}
			else
			{
				dragStart = Point.MINUS_ONE

				if (value.targets == 0)
				{
					value.activate(this)
					field = null
				}
			}
		}

	lateinit var updateFuns: kotlin.Array<() -> Boolean>
	var inTurn = false
	var gainedBonusPower = false
	var matchCount = 0

	// ----------------------------------------------------------------------
	init
	{
		updateFuns = arrayOf(
				fun() = cascade(),
				fun() = match(),
				fun() = sink(),
				fun() = detonate()
		)

		onTurn += {

			for (tile in grid)
			{
				val orb = tile.orb
				if (orb != null)
				{
					// Process attacks
					if (orb.hasAttack)
					{
						orb.attackTimer--
					}
					if (orb.isChanger)
					{
						orb.desc = orb.nextDesc!!
						orb.nextDesc = Orb.getRandomOrb(level, orb.desc)

						val effect = AssetManager.loadSprite("EffectSprites/Heal/Heal", 0.05f, orb.desc.sprite.colour)
						tile.effects.add(effect)
					}
				}

				// process creatures
				val creature = tile.creature
				if (creature != null && tile == creature.tiles[0, 0])
				{
					creature.onTurn(this@Grid)
					creature.damSources.clear()
				}
			}

			gainedBonusPower = false

			false
		}

		onPop += fun (orb: Orb, delay: Float) : Boolean {

			if (!orb.skipPowerOrb)
			{
				val pos = GridWidget.instance.pointToScreenspace(orb)
				val dst = PowerBar.instance.getOrbDest()
				val sprite = AssetManager.loadSprite("Oryx/uf_split/uf_items/crystal_sky")

				if (dst != null)
				{
					Future.call({ Mote(pos, dst, sprite, { PowerBar.instance.power++ }) }, delay)

					if (!gainedBonusPower)
					{
						gainedBonusPower = true

						for (i in 0..level.player.powerGain-1)
						{
							Future.call({ Mote(pos, dst, sprite, { PowerBar.instance.power++ }) }, delay)
						}
					}
				}
			}

			return false
		}
	}

	// ----------------------------------------------------------------------
	fun activateAbility()
	{
		activeAbility!!.activate(level.grid)
		activeAbility = null
		onTurn()
	}

	// ----------------------------------------------------------------------
	fun getSpecial(count1: Int, count2: Int, dir: Direction, orb: Orb): Special?
	{
		if (count1 >= 5 || count2 >= 5)
		{
			return Match5(orb)
		}
		else if (count1 > 0 && count2 > 0)
		{
			return DualMatch(orb)
		}
		else if (dir.y != 0 && count1 == 4)
		{
			return Vertical4(orb)
		}
		else if (dir.x != 0 && count1 == 4)
		{
			return Horizontal4(orb)
		}

		return null
	}

	// ----------------------------------------------------------------------
	fun select(newSelection: Point)
	{
		if (hasAnim() || level.completed) return

		if (activeAbility != null)
		{
			val newTile = tile(newSelection) ?: return
			if (!activeAbility!!.targetter.isValid(newTile, activeAbility!!.data)) return

			if (newTile.isSelected)
			{
				newTile.isSelected = false
				activeAbility!!.selectedTargets.removeValue(newTile, true)
			}
			else if (activeAbility!!.selectedTargets.size < activeAbility!!.targets)
			{
				newTile.isSelected = true
				activeAbility!!.selectedTargets.add(newTile)
			}
		}
		else
		{
			dragStart = newSelection
		}
	}

	// ----------------------------------------------------------------------
	fun dragEnd(selection: Point)
	{
		if (selection != dragStart && dragStart.dist(selection) == 1)
		{
			toSwap = Pair(dragStart, selection)
			dragStart = Point.MINUS_ONE
		}
	}

	// ----------------------------------------------------------------------
	fun clearDrag()
	{
		dragStart = Point.MINUS_ONE
	}

	// ----------------------------------------------------------------------
	fun cascade(): Boolean
	{
		for (x in 0..width - 1) for (y in 0..height -1 )
		{
			spawnCount[x, y] = 0

			val tile = grid[x, y]
			val swappable = tile.swappable

			if (swappable != null)
			{
				if (swappable.cascadeCount == -1)
				{
					swappable.cascadeCount = 1
				}
				else
				{
					swappable.cascadeCount = 0
				}
			}
		}

		var cascadeComplete = false

		var cascadeCount = 0

		while (!cascadeComplete)
		{
			cascadeComplete = true

			for (x in 0..width - 1)
			{
				val done = cascadeColumn(x, cascadeCount)
				if (!done) cascadeComplete = false
			}

			cascadeCount++
		}

		cascadeComplete = makeAnimations()

		return cascadeComplete
	}

	// ----------------------------------------------------------------------
	fun cascadeColumn(x: Int, cascadeCount: Int) : Boolean
	{
		var complete = true

		var currentY = height-1
		while (currentY >= 0)
		{
			val tile = grid[x, currentY]

			// read up column, find first gap
			if (tile.canHaveOrb && tile.swappable == null && tile.creature == null)
			{
				// if gap found read up until solid / spawner
				var found: Tile? = null

				for (searchY in currentY downTo -1)
				{
					val stile = if (searchY >= 0) grid[x, searchY] else null
					if (stile == null)
					{
						found = tile
						break
					}
					else if (stile.swappable != null)
					{
						val swappable = stile.swappable!!
						val orb = swappable as? Orb
						if (orb == null || (orb.armed == null && !orb.sealed)) found = stile
						break
					}
					else if (stile.chest != null)
					{
						found = stile
						break
					}
					else if (!stile.canHaveOrb && !stile.isPit)
					{
						break
					}
					else if (stile.block != null)
					{
						break
					}
					else if (stile.creature != null)
					{
						break
					}
				}

				// pull solid / spawn new down
				if (found != null)
				{
					var orb: Swappable? = null

					if (found == tile)
					{
						orb = level.spawnOrb()
						orb.movePoints.add(Point(x, -1))
						orb.spawnCount = spawnCount[x, 0]

						spawnCount[x, 0]++

						onSpawn(orb)
					}
					else if (found.chest != null)
					{
						val o = found.chest!!.spawn(this)
						if (o != null)
						{
							orb = o
							orb.movePoints.add(Point(x, found.y))
							orb.spawnCount = spawnCount[x, found.y + 1]

							spawnCount[x, found.y + 1]++

							onSpawn(orb)
						}
					}
					else
					{
						orb = found.swappable!!
						found.swappable = null
						if (orb.movePoints.size == 0) orb.movePoints.add(found)
					}

					if (orb != null)
					{
						orb.movePoints.add(tile)
						tile.swappable = orb
						orb.cascadeCount = cascadeCount

						complete = false
					}
				}
			}

			currentY--
		}

		// walk down column
		// each block with a clear, push 1 orb into the top from a neighbour

		if (complete)
		{
			currentY = 0
			var lookingForOrb = 0 // 0 = not looking, 1 = looking, 2 = placed
			while (currentY < height)
			{
				val tile = grid[x, currentY]
				if (tile.canHaveOrb && tile.swappable == null && tile.block == null && tile.creature == null)
				{
					if (lookingForOrb == 0)
					{
						lookingForOrb = 1
					}
				}
				else if (!tile.canHaveOrb || lookingForOrb == 2 || tile.block != null || tile.creature != null)
				{
					lookingForOrb = 0
				}
				else if (tile.swappable != null)
				{
					lookingForOrb = 0
				}

				if (lookingForOrb == 1)
				{
					// check neighbours for orb
					val diagL = tile(x - 1, currentY - 1)
					val diagLBelow = tile(x - 1, currentY)
					val diagR = tile(x + 1, currentY - 1)
					val diagRBelow = tile(x + 1, currentY)

					val diagLValid = diagL != null && diagL.swappable != null && diagL.swappable!!.canMove && (diagLBelow == null || !diagLBelow.canHaveOrb || diagLBelow.contents != null)
					val diagRValid = diagR != null && diagR.swappable != null && diagR.swappable!!.canMove && (diagRBelow == null || !diagRBelow.canHaveOrb || diagRBelow.contents != null)

					if (diagLValid || diagRValid)
					{
						fun pullIn(t: Tile)
						{
							val orb = t.swappable!!
							t.swappable = null

							if (orb.movePoints.size == 0) orb.movePoints.add(t)

							tile.swappable = orb
							orb.cascadeCount = cascadeCount

							orb.movePoints.add(tile)

							complete = false
						}

						// if found one, pull in and set to 2
						if (diagLValid && diagRValid)
						{
							if (MathUtils.randomBoolean())
							{
								pullIn(diagL!!)
							}
							else
							{
								pullIn(diagR!!)
							}
						}
						else if (diagLValid)
						{
							pullIn(diagL!!)
						}
						else
						{
							pullIn(diagR!!)
						}

						lookingForOrb = 2
					}


				}

				currentY++
			}
		}

		return complete
	}

	// ----------------------------------------------------------------------
	fun makeAnimations(): Boolean
	{
		var doneAnimation = true

		for (x in 0..width-1)
		{
			for (y in 0..height-1)
			{
				val orb = grid[x, y].swappable ?: continue

				if (orb.movePoints.size > 0)
				{
					val firstIsNull = orb.spawnCount >= 0

					val pathPoints = Array(orb.movePoints.size){ i -> Vector2(orb.movePoints[i].x.toFloat(), orb.movePoints[i].y.toFloat()) }
					for (point in pathPoints)
					{
						point.x -= pathPoints.last().x
						point.y = pathPoints.last().y - point.y
					}

					val path = UnsmoothedPath(pathPoints)

					orb.sprite.animation = MoveAnimation.obtain().set(0.1f + pathPoints.size * animSpeed, path, Interpolation.exp5In)
					orb.sprite.renderDelay = orb.spawnCount * 0.1f
					orb.spawnCount = -1

					if (firstIsNull)
					{
						orb.sprite.animation = ExpandAnimation.obtain().set(animSpeed)
						orb.sprite.showBeforeRender = false
					}

					orb.movePoints.clear()

					doneAnimation = false
				}
			}
		}

		return doneAnimation
	}

	// ----------------------------------------------------------------------
	fun hasAnim(): Boolean
	{
		var hasAnim = false
		for (x in 0..width-1)
		{
			for (y in 0..height-1)
			{
				val tile = grid[x, y]
				if (tile.effects.size > 0)
				{
					hasAnim = true
					break
				}

				val orb = tile.swappable
				if (orb != null && orb.sprite.animation != null)
				{
					hasAnim = true
					break
				}
			}
		}

		return hasAnim
	}

	// ----------------------------------------------------------------------
	fun update(delta: Float): Boolean
	{
		var done = true

		// if in update, do animations
		cleanup(delta)

		if (!hasAnim())
		{
			for (f in updateFuns)
			{
				val complete = f()
				if (!complete)
				{
					noMatchTimer = 0f
					done = false
					return done
				}
			}

			if (inTurn)
			{
				onTurn()
				inTurn = false
			}

			matchCount = 0

			if (!level.completed && FullscreenMessage.instance == null)
			{
				if (activeAbility == null) matchHint = findValidMove()
				if (activeAbility == null && matchHint == null)
				{
					FullscreenMessage("No valid moves. Randomising.", "", { refill() }).show()
				}
				else
				{
					if (activeAbility == null) noMatchTimer += delta

					// handle input
					if (toSwap != null)
					{
						val swapSuccess = swap()
						if (swapSuccess) inTurn = true
					}

					onTime(delta)
				}
			}
		}
		else
		{
			done = false
		}

		return done
	}

	// ----------------------------------------------------------------------
	fun swap(): Boolean
	{
		val oldTile = tile(toSwap!!.first)
		val newTile = tile(toSwap!!.second)

		toSwap = null

		if (oldTile == null || newTile == null) return false

		val oldSwap = oldTile.swappable ?: return false
		val newSwap = newTile.swappable ?: return false

		val oldOrb = oldSwap as? Orb
		val newOrb = newSwap as? Orb

		if (oldSwap.sealed || newSwap.sealed) return false

		oldSwap.cascadeCount = -1
		newSwap.cascadeCount = -1

		if (oldOrb != null && newOrb != null)
		{
			// check for merges
			if (newOrb.special != null || oldOrb.special != null)
			{
				val armfun = newOrb.special?.merge(oldOrb) ?: oldOrb.special?.merge(newOrb)
				if (armfun != null)
				{
					val sprite = oldOrb.sprite.copy()
					sprite.animation = MoveAnimation.obtain().set(animSpeed, UnsmoothedPath(newTile.getPosDiff(oldTile)), Interpolation.linear)
					newTile.effects.add(sprite)

					onPop(oldOrb, 0f)
					oldTile.orb = null

					newOrb.armed = armfun
					newOrb.markedForDeletion = true

					return false
				}
			}
		}

		oldTile.swappable = newSwap
		newTile.swappable = oldSwap

		val matches = findMatches()
		for (match in matches) match.free()
		if (matches.size == 0)
		{
			oldTile.swappable = oldSwap
			newTile.swappable = newSwap

			oldSwap.sprite.animation = BumpAnimation.obtain().set(animSpeed, Direction.Companion.getDirection(oldTile, newTile))
			return false
		}
		else
		{
			lastSwapped = newTile

			oldSwap.sprite.animation = MoveAnimation.obtain().set(animSpeed, UnsmoothedPath(newTile.getPosDiff(oldTile)), Interpolation.linear)
			newSwap.sprite.animation = MoveAnimation.obtain().set(animSpeed, UnsmoothedPath(oldTile.getPosDiff(newTile)), Interpolation.linear)
			return true
		}
	}

	// ----------------------------------------------------------------------
	fun cleanup(delta: Float)
	{
		for (x in 0..width-1)
		{
			for (y in 0..height - 1)
			{
				val tile = grid[x, y]

				if (tile.orb != null)
				{
					val orb = tile.orb!!
					orb.x = x
					orb.y = y

					if (orb.markedForDeletion && orb.sprite.animation == null && orb.armed == null)
					{
						tile.orb = null
						onPop(orb, orb.deletionEffectDelay)

						if (orb.deletionEffectDelay >= 0.2f && orb.special == null)
						{
							val sprite = orb.sprite.copy()
							sprite.renderDelay = orb.deletionEffectDelay - 0.2f
							sprite.showBeforeRender = true
							sprite.animation = AlphaAnimation.obtain().set(0.2f, 1f, 0f, sprite.colour)
							tile.effects.add(sprite)
						}
					}
					else if (orb.hasAttack && orb.attackTimer == 0 && orb.sprite.animation == null)
					{
						if (!level.completed) onAttacked(orb)
						tile.orb = null
						onPop(orb, orb.deletionEffectDelay)
					}
					else if (orb.delayDisplayAttack > 0)
					{
						orb.delayDisplayAttack -= delta

						if (orb.delayDisplayAttack <= 0)
						{
							orb.hasAttack = true
						}
					}
				}
				else if (tile.block != null)
				{
					val block = tile.block!!
					if (block.count <= 0)
					{
						tile.block = null
						tile.effects.add(block.death.copy())
					}
				}
				else if (tile.shield != null)
				{
					val shield = tile.shield!!
					if (shield.count <= 0)
					{
						tile.shield = null
					}
				}
				else if (tile.creature != null)
				{
					val creature = tile.creature!!
					if (creature.hp <= 0)
					{
						for (t in creature.tiles)
						{
							t.creature = null
						}

						val death = creature.death
						death.size[0] = creature.size
						death.size[1] = creature.size

						tile.effects.add(death)

						if (creature is Monster)
						{
							for (reward in creature.rewards)
							{
								if (MathUtils.random(100) < reward.value.second)
								{
									for (i in 1..reward.value.first)
									{
										if (reward.key == "Gold")
										{
											val pos = GridWidget.instance.pointToScreenspace(tile)
											val dst = GridScreen.instance.playerPortrait.localToStageCoordinates(Vector2())
											val sprite = level.theme.coin.copy()

											if (dst != null)
											{
												Mote(pos, dst, sprite, { level.player.gold++ })
											}
										} else
										{
											val item = Item.load(reward.key)

											val pos = GridWidget.instance.pointToScreenspace(tile)
											val dst = GridScreen.instance.playerPortrait.localToStageCoordinates(Vector2())
											val sprite = item.icon.copy()

											if (dst != null)
											{
												Mote(pos, dst, sprite, { level.player.addItem(item) })
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	fun match(): Boolean
	{
		val matches = findMatches(3)
		clearMatches(matches)
		for (match in matches) match.free()

		lastSwapped = Point.MINUS_ONE

		if (matches.size > 0)
		{
			matchCount++

			val chosen = matches.random()
			val point = chosen.points().asSequence().random()
			displayMatchMessage(point!!)
		}

		return matches.size == 0
	}

	// ----------------------------------------------------------------------
	fun displayMatchMessage(point: Point)
	{
		data class MessageData(val text: String, val colour: Colour, val size: Float)
		val message = when(matchCount)
		{
			4 -> MessageData("Impressive", Colour(0.8f, 0.9f, 1f, 1f), 1f)
			7 -> MessageData("Amazing", Colour(0.8f, 1f, 0.9f, 1f), 1.3f)
			10 -> MessageData("Spectacular", Colour(1f, 0.8f, 0.9f, 1f), 1.6f)
			14 -> MessageData("Magical", Colour(0.2f, 0.82f, 1f, 1f), 1.9f)
			18 -> MessageData("Legendary", Colour(1f, 0.81f, 0.5f, 1f), 2.2f)
			22 -> MessageData("Mythical", Colour(0.8f, 0.5f, 0.95f, 1f), 2.5f)
			26 -> MessageData("Divine", Colour(0.95f, 1f, 0.81f, 1f), 2.8f)
			30 -> MessageData("Godlike", Colour(0.8f, 0.55f, 0.78f, 1f), 3.1f)
			else -> null
		}
		val maxVal = 32f

		if (message != null)
		{
			val pos = GridWidget.instance.pointToScreenspace(point)

			val sequence = alpha(0f) then fadeIn(0.1f) then parallel(moveBy(MathUtils.random(-2f, 2f), MathUtils.random(0f, 2f), 1f), shake(matchCount.toFloat() / maxVal, 0.03f, 1f)) then fadeOut(0.1f) then removeActor()

			val label = Label(message.text, Global.skin, "popup")
			label.color = message.colour.color()
			label.setFontScale(message.size)
			label.rotation = -60f
			label.setPosition(pos.x, pos.y)
			label + sequence

			val width = label.prefWidth
			if (pos.x + width > Global.stage.width)
			{
				label.setPosition(Global.stage.width - width - 20, pos.y)
			}

			Global.stage.addActor(label)
		}
	}

	// ----------------------------------------------------------------------
	fun findValidMove() : Pair<Point, Point>?
	{
		// find all 2 matches
		val matches = findMatches(2)

		for (match in matches)
		{
			// check the 3 tiles around each end to see if it contains one of the correct colours
			val dir = match.direction()
			val key = grid[match.p1].orb!!.key

			fun checkSurrounding(point: Point, dir: Direction, key: Int): Pair<Point, Point>?
			{
				val targetTile = tile(point)
				if (targetTile == null || targetTile.swappable == null || !targetTile.swappable!!.canMove) return null

				fun canMatch(point: Point): Boolean
				{
					val tile = tile(point) ?: return false
					val orb = tile.orb ?: return false
					if (!orb.canMove || orb.markedForDeletion) return false
					return orb.key == key
				}

				// check + dir
				if (canMatch(point + dir)) return Pair(point, point+dir)
				if (canMatch(point + dir.cardinalClockwise)) return Pair(point, point+dir.cardinalClockwise)
				if (canMatch(point + dir.cardinalAnticlockwise)) return Pair(point, point+dir.cardinalAnticlockwise)

				return null
			}

			// the one before first is at first-dir
			val beforeFirst = match.p1 + dir.opposite
			val beforeFirstPair = checkSurrounding(beforeFirst, dir.opposite, key)
			if (beforeFirstPair != null)
			{
				for (match in matches) match.free()
				return beforeFirstPair
			}

			val afterSecond = match.p2 + dir
			val afterSecondPair = checkSurrounding(afterSecond, dir, key)
			if (afterSecondPair != null)
			{
				for (match in matches) match.free()
				return afterSecondPair
			}
		}

		for (match in matches) match.free()

		fun getTileKey(x: Int, y: Int, dir: Direction): Int
		{
			val tile = tile(x + dir.x, y + dir.y) ?: return -1
			val orb = tile.orb ?: return -1
			if (!orb.canMove || orb.markedForDeletion) return -1

			return orb.key
		}

		// check diamond pattern
		for (x in 0..width-1)
		{
			for (y in 0..height - 1)
			{
				val tile = tile(x, y) ?: continue
				val swappable = tile.swappable ?: continue
				if (!swappable.canMove) continue

				for (dir in Direction.CardinalValues)
				{
					val key = getTileKey(x, y, dir)
					if (key != -1)
					{
						val k1 = getTileKey(x, y, dir.cardinalClockwise)
						val k2 = getTileKey(x, y, dir.cardinalAnticlockwise)

						if (key == k1 && key == k2)
						{
							return Pair(Point(x, y), Point(x + dir.x, y + dir.y))
						}
					}
				}
			}
		}

		// check for special merges
		for (x in 0..width-1)
		{
			for (y in 0..height - 1)
			{
				val orb = grid[x, y].orb ?: continue
				if (orb.special != null)
				{
					for (dir in Direction.CardinalValues)
					{
						val tile = tile(x + dir.x, y + dir.y) ?: continue
						if (tile.orb?.special != null)
						{
							return Pair(Point(x, y), Point(x + dir.x, y + dir.y))
						}
					}
				}
			}
		}

		// else no valid

		return null
	}

	// ----------------------------------------------------------------------
	fun detonate(): Boolean
	{
		var complete = true

		val tilesToDetonate = Array<Tile>()

		for (x in 0..width-1)
		{
			for (y in 0..height - 1)
			{
				val tile = grid[x, y]
				val orb = tile.orb ?: continue

				if (orb.armed != null)
				{
					tilesToDetonate.add(tile)
				}
			}
		}

		for (tile in tilesToDetonate)
		{
			tile.orb!!.armed!!.invoke(tile, this, tile.orb!!)

			tile.orb!!.armed = null
			complete = false
		}

		if (!complete)
		{
			matchCount++
			val point = tilesToDetonate.random()
			displayMatchMessage(point!!)
		}

		return complete
	}

	// ----------------------------------------------------------------------
	fun sink(): Boolean
	{
		var complete = true

		for (x in 0..width-1)
		{
			val tile = grid[x, height-1]
			val sink = tile.sinkable
			if (sink != null)
			{
				sink.x = x
				sink.y = height-1

				tile.sinkable = null
				onSunk(sink)

				complete = false
			}
		}

		return complete
	}

	// ----------------------------------------------------------------------
	fun refill()
	{
		val tempgrid: Array2D<Tile> = Array2D(width, height ){ x, y -> Tile(x, y) }
		for (x in 0..width-1)
		{
			for (y in 0..height - 1)
			{
				tempgrid[x, y].contents = grid[x, y].contents
			}
		}

		fill(true)

		for (x in 0..width-1)
		{
			for (y in 0..height - 1)
			{
				val oldorb = tempgrid[x, y].swappable
				if (oldorb == null || oldorb !is Orb) grid[x, y].contents = tempgrid[x, y].contents
				else
				{
					val orb = grid[x, y].orb!!

					if (oldorb.special != null) orb.special = oldorb.special!!.copy(orb)
					orb.sealCount = oldorb.sealCount
					if (oldorb.hasAttack)
					{
						orb.hasAttack = true
						orb.attackTimer = oldorb.attackTimer
					}
					if (oldorb.isChanger)
					{
						orb.isChanger = true
						orb.nextDesc = oldorb.nextDesc
					}

					val delay = grid[x, y].taxiDist(Point.ZERO).toFloat() * 0.1f
					orb.sprite.renderDelay = delay + 0.2f
					orb.sprite.showBeforeRender = false

					val sprite = refillSprite.copy()
					sprite.colour = orb.sprite.colour
					sprite.renderDelay = delay
					sprite.showBeforeRender = false

					grid[x, y].effects.add(sprite)
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	fun fill(orbOnly: Boolean)
	{
		for (x in 0..width-1)
		{
			for (y in 0..height-1)
			{
				if (grid[x, y].canHaveOrb && grid[x, y].block == null && grid[x, y].creature == null)
				{
					val toSpawn = if (orbOnly) Orb(Orb.getRandomOrb(level), level.theme) else level.spawnOrb()

					if (toSpawn is Orb)
					{
						val valid = Orb.getValidOrbs(level)
						val l1 = tile(x - 1, y)
						val l2 = tile(x - 2, y)
						val u1 = tile(x, y - 1)
						val u2 = tile(x, y - 2)

						if (l1?.orb != null && l2?.orb != null && l1?.orb?.key == l2?.orb?.key)
						{
							valid.removeValue(l1!!.orb!!.desc, true)
						}
						if (u1?.orb != null && u2?.orb != null && u1?.orb?.key == u2?.orb?.key)
						{
							valid.removeValue(u1!!.orb!!.desc, true)
						}

						toSpawn.desc = valid.random()
					}

					grid[x, y].contents = toSpawn
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	fun findMatches() : Array<Match>
	{
		val matches = Array<Match>(false, 16)

		matches.addAll(findMatches(3))
		matches.addAll(findMatches(4))
		matches.addAll(findMatches(5))

		// clear duplicates
		var i = 0
		while (i < matches.size)
		{
			val pair = matches[i]

			var ii = i+1
			while (ii < matches.size)
			{
				val opair = matches[ii]

				if (opair == pair)
				{
					matches.removeIndex(ii)
				}
				else
				{
					ii++
				}
			}

			i++
		}

		return matches
	}

	// ----------------------------------------------------------------------
	fun findMatches(length: Int, exact: Boolean = false) : Array<Match>
	{
		val matches = Array<Match>()

		fun addMatch(p1: Point, p2: Point)
		{
			fun check(dst: Int): Boolean
			{
				if (exact) return dst == length-1
				else return dst >= length-1
			}

			val dst = p1.dist(p2)
			if (check(dst))
			{
				// check not already added
				matches.add(Match(p1, p2))
			}
		}

		// Match rows
		for (y in 0..height-1)
		{
			var sx = -1
			var key = -1

			for (x in 0..width-1)
			{
				val tile = grid[x, y]
				val orb = tile.orb

				if (orb == null)
				{
					if (key != -1)
					{
						addMatch(Point.obtain().set(sx,y), Point.obtain().set(x-1,y))
					}

					key = -1
				}
				else
				{
					if (orb.key != key || orb.armed != null)
					{
						// if we were matching, close matching
						if (key != -1)
						{
							addMatch(Point.obtain().set(sx,y), Point.obtain().set(x-1,y))
						}

						sx = x
						key = orb.key
					}
				}
			}

			if (key != -1)
			{
				addMatch(Point.obtain().set(sx,y), Point.obtain().set(width-1,y))
			}
		}

		// Match columns
		for (x in 0..width-1)
		{
			var sy = -1
			var key = -1

			for (y in 0..height-1)
			{
				val tile = grid[x, y]
				val orb = tile.orb

				if (orb == null)
				{
					if (key != -1)
					{
						addMatch(Point.obtain().set(x,sy), Point.obtain().set(x,y-1))
					}

					key = -1
				}
				else
				{
					if (orb.key != key || orb.armed != null)
					{
						// if we were matching, close matching
						if (key != -1)
						{
							addMatch(Point.obtain().set(x,sy), Point.obtain().set(x,y-1))
						}

						sy = y
						key = orb.key
					}
				}
			}

			if (key != -1)
			{
				addMatch(Point.obtain().set(x,sy), Point.obtain().set(x,height-1))
			}
		}

		return matches
	}

	// ----------------------------------------------------------------------
	fun clearMatches(matches: Array<Match>)
	{
		// mark all matched tiles with the matches associated with them
		for (tile in grid)
		{
			tile.associatedMatches[0] = null
			tile.associatedMatches[1] = null
		}

		for (match in matches)
		{
			for (point in match.points())
			{
				val tile = grid[point]
				if (tile.associatedMatches[0] == null)
				{
					tile.associatedMatches[0] = match
				}
				else
				{
					tile.associatedMatches[1] = match
				}
			}
		}

		val coreTiles = Array<Tile>()
		val borderTiles = ObjectSet<Tile>()

		// remove all orbs, activate all specials
		for (match in matches)
		{
			coreTiles.clear()
			borderTiles.clear()

			for (point in match.points())
			{
				coreTiles.add(grid[point])
				pop(point.x, point.y, 0f)
			}

			for (tile in coreTiles)
			{
				for (d in Direction.CardinalValues)
				{
					val t = tile(tile.x + d.x, tile.y + d.y) ?: continue
					if (!coreTiles.contains(t, true))
					{
						borderTiles.add(t)
					}
				}
			}

			// pop all borders
			for (t in borderTiles)
			{
				if (t.block != null)
				{
					t.block!!.count--

					t.effects.add(hitEffect.copy())
				}
				if (t.creature != null)
				{
					t.creature!!.hp -= if (!t.creature!!.damSources.contains(this)) level.player.attackDam else 1
					t.creature!!.damSources.add(this)
					onDamaged(t.creature!!)

					t.effects.add(hitEffect.copy())
				}
				if (t.shield != null)
				{
					t.shield!!.count--

					t.effects.add(hitEffect.copy())
				}
			}
		}

		// for each tile with 2 matches spawn the relevant special, and mark the matches as used, if cross point is used them spawn in a neighbouring tile that isnt specialed
		for (tile in grid)
		{
			if (tile.associatedMatches[0] != null && tile.associatedMatches[1] != null)
			{
				val orb = Orb(tile.orb!!.desc, level.theme)
				val special = getSpecial(tile.associatedMatches[0]!!.length(), tile.associatedMatches[1]!!.length(), Direction.CENTRE, orb) ?: continue
				orb.special = special

				if (tile.orb != null)
				{
					if (tile.orb!!.special != null)
					{
						orb.armed = orb.special!!.merge(tile.orb!!) ?: tile.orb!!.special!!.merge(orb)
						orb.markedForDeletion = true
					}

					tile.orb!!.x = tile.x
					tile.orb!!.y = tile.y
					onPop(tile.orb!!, 0f)
				}

				tile.orb = orb

				tile.associatedMatches[0]!!.used = true
				tile.associatedMatches[1]!!.used = true

				for (point in tile.associatedMatches[0]!!.points())
				{
					val sprite = orb.sprite.copy()
					sprite.drawActualSize = false
					sprite.animation = MoveAnimation.obtain().set(animSpeed, UnsmoothedPath(tile.getPosDiff(point)), Interpolation.linear)

					tile.effects.add(sprite)
				}

				for (point in tile.associatedMatches[1]!!.points())
				{
					val sprite = orb.sprite.copy()
					sprite.drawActualSize = false
					sprite.animation = MoveAnimation.obtain().set(animSpeed, UnsmoothedPath(tile.getPosDiff(point)), Interpolation.linear)

					tile.effects.add(sprite)
				}
			}
		}

		// for each unused match spawn the relevant special at the player swap pos, else at the center, else at a random unspecialed tile
		for (match in matches)
		{
			if (!match.used && match.length() > 3)
			{
				val tile = grid[match.points().maxBy { grid[it].orb?.cascadeCount ?: 0 }!!]

				val orb = Orb(tile.orb!!.desc, level.theme)
				val special = getSpecial(match.length(), 0, match.direction(), orb) ?: continue
				orb.special = special

				if (tile.orb != null)
				{
					if (tile.orb!!.special != null)
					{
						orb.armed = orb.special!!.merge(tile.orb!!) ?: tile.orb!!.special!!.merge(orb)
						orb.markedForDeletion = true
					}

					tile.orb!!.x = tile.x
					tile.orb!!.y = tile.y
					onPop(tile.orb!!, 0f)
				}

				tile.orb = orb

				for (point in match.points())
				{
					val sprite = orb.sprite.copy()
					sprite.drawActualSize = false
					sprite.animation = MoveAnimation.obtain().set(animSpeed, UnsmoothedPath(tile.getPosDiff(point)), Interpolation.linear)

					tile.effects.add(sprite)
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	fun pop(point: Point, delay: Float, damSource: Any? = null, bonusDam: Int = 0, skipPowerOrb: Boolean = false)
	{
		pop(point.x, point.y , delay, damSource, bonusDam, skipPowerOrb)
	}

	// ----------------------------------------------------------------------
	fun pop(x: Int, y: Int, delay: Float, damSource: Any? = null, bonusDam: Int = 0, skipPowerOrb: Boolean = false)
	{
		val tile = tile(x, y) ?: return

		if (tile.hasPlate)
		{
			tile.plateStrength--
			val hit = hitEffect.copy()
			hit.renderDelay = delay
			tile.effects.add(hit)
		}

		if (tile.block != null)
		{
			tile.block!!.count--
			val hit = hitEffect.copy()
			hit.renderDelay = delay
			tile.effects.add(hit)
			return
		}

		if (tile.shield != null)
		{
			tile.shield!!.count--
			val hit = hitEffect.copy()
			hit.renderDelay = delay
			tile.effects.add(hit)
			return
		}

		if (tile.creature != null)
		{
			tile.creature!!.hp -= if (!tile.creature!!.damSources.contains(damSource)) 1 + bonusDam else 1
			if (damSource != null) tile.creature!!.damSources.add(damSource)
			val hit = hitEffect.copy()
			hit.renderDelay = delay
			tile.effects.add(hit)
			onDamaged(tile.creature!!)
			return
		}

		val orb = tile.orb ?: return
		if (orb.markedForDeletion) return // already completed, dont do it again
		if (orb !is Orb) return

		if (orb.sealed)
		{
			orb.sealCount--
			val hit = hitEffect.copy()
			hit.renderDelay = delay
			tile.effects.add(hit)
			return
		}

		orb.markedForDeletion = true
		orb.deletionEffectDelay = delay
		orb.skipPowerOrb = skipPowerOrb

		if (orb.armed != null)
		{
			// dont need to do anything more
		}
		else if (orb.special == null)
		{
			orb.sprite.visible = false

			val sprite = orb.desc.death.copy()
			sprite.colour = orb.sprite.colour
			sprite.renderDelay = delay

			tile.effects.add(sprite)
		}
		else if (orb.special is Match5)
		{
			orb.markedForDeletion = false
		}
		else
		{
			orb.armed = orb.special!!.apply()
		}
	}

	// ----------------------------------------------------------------------
	fun tile(point: Point): Tile? = tile(point.x, point.y)

	// ----------------------------------------------------------------------
	fun tile(x: Int, y:Int): Tile?
	{
		if (x >= 0 && y >= 0 && x < width && y < height) return grid[x, y]
		else return null
	}
}

data class Match(val p1: Point, val p2: Point, var used: Boolean = false)
{
	fun length() = p1.dist(p2) + 1
	fun points() = p1.rangeTo(p2)
	fun direction() = Direction.getDirection(p1, p2)
	fun free()
	{
		p1.free()
		p2.free()
	}
}
