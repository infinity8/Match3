package com.lyeeedar.Board

import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Util.Point
import com.lyeeedar.Util.tryGet

abstract class Swappable(val theme: LevelTheme) : Point()
{
	lateinit var sprite: Sprite

	val movePoints = Array<Point>()
	var spawnCount = -1
	var cascadeCount = 0

	abstract val canMove: Boolean

	var sealSprite: Sprite = theme.sealSprites.tryGet(0).copy()
	var sealCount = 0
		set(value)
		{
			field = value
			sealSprite = theme.sealSprites.tryGet(sealCount-1).copy()
		}
	val sealed: Boolean
		get() = sealCount > 0
}
