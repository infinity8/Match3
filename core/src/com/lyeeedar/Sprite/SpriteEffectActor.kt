package com.lyeeedar.Sprite

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lyeeedar.Global
import com.lyeeedar.Sprite.Sprite

open class SpriteEffectActor(val sprite: Sprite, val w: Float, val h: Float, val pos: Vector2, val completionFunc: (() -> Unit)? = null): Actor()
{
	init
	{
		Global.stage.addActor(this)
	}

	override fun act(delta: Float)
	{
		super.act(delta)
		val complete = sprite.update(delta)
		if (complete)
		{
			completionFunc?.invoke()
			remove()
		}
	}

	override fun draw(batch: Batch?, parentAlpha: Float)
	{
		super.draw(batch, parentAlpha)

		var x = pos.x
		var y = pos.y

		if ( sprite.spriteAnimation != null )
		{
			val offset = sprite.spriteAnimation?.renderOffset()

			if (offset != null)
			{
				x += offset[0]
				y += offset[1]
			}
		}

		sprite.render(batch as SpriteBatch, x, y, w, h)
	}
}
