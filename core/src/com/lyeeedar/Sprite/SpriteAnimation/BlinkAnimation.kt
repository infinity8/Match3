package com.lyeeedar.Sprite.SpriteAnimation

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.Pools
import com.badlogic.gdx.utils.XmlReader

/**
 * Created by Philip on 31-Jul-16.
 */

class BlinkAnimation() : AbstractColourAnimation()
{
	private var duration: Float = 0f
	private var time: Float = 0f
	private val colour: Color = Color()
	private val targetColour: Color = Color()
	private val startColour: Color = Color()

	override fun duration(): Float = duration
	override fun time(): Float = time
	override fun renderColour(): Color = colour

	override fun update(delta: Float): Boolean
	{
		time += delta

		val alpha = MathUtils.clamp(Math.abs((time - duration / 2) / (duration / 2)), 0f, 1f)

		colour.set(targetColour).lerp(startColour, alpha)

		if (time >= duration)
		{
			if (oneTime) return true
			else time -= duration
		}

		return false
	}

	fun set(target: Color, start: Color, duration: Float, oneTime: Boolean = true): BlinkAnimation
	{
		this.targetColour.set(target)
		this.startColour.set(start)
		this.duration = duration
		this.oneTime = oneTime

		this.time = 0f
		this.colour.set(start)

		return this
	}

	override fun parse(xml: XmlReader.Element)
	{
	}

	override fun copy(): AbstractSpriteAnimation = BlinkAnimation.obtain().set(targetColour, startColour, duration, oneTime)

	var obtained: Boolean = false
	companion object
	{
		private val pool: Pool<BlinkAnimation> = Pools.get( BlinkAnimation::class.java, Int.MAX_VALUE )

		@JvmStatic fun obtain(): BlinkAnimation
		{
			val anim = BlinkAnimation.pool.obtain()

			if (anim.obtained) throw RuntimeException()

			anim.obtained = true
			anim.time = 0f
			return anim
		}
	}
	override fun free() { if (obtained) { BlinkAnimation.pool.free(this); obtained = false } }
}