package com.lyeeedar.Board

import com.lyeeedar.Renderables.Sprite.Sprite

class Sinkable : Swappable
{
	override val canMove: Boolean
		get() = !sealed

	constructor(sprite: Sprite, theme: LevelTheme)
		: super(theme)
	{
		this.sprite = sprite
	}


}
