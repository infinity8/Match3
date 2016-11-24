package com.lyeeedar.Renderables.Sprite

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.HDRColourSpriteBatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Animation.AbstractAnimation
import com.lyeeedar.Renderables.Animation.AbstractColourAnimation
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.Point

class Sprite(val fileName: String, var animationDelay: Float, var textures: Array<TextureRegion>, colour: Colour, mode: Sprite.AnimationMode, var drawActualSize: Boolean) : Renderable()
{
	enum class AnimationStage
	{
		INVALID,
		START,
		MIDDLE,
		END;

		companion object
		{
			val Values = values()
		}
	}

	enum class AnimationMode
	{
		NONE, TEXTURE, SHRINK, SINE
	}

	var referenceSize: Float? = null

	var tempCol = Colour()
	var colour = Colour(1f,1f,1f,1f)
	var colourAnimation: AbstractColourAnimation? = null

	var repeatDelay = 0f
	var repeatAccumulator: Float = 0.toFloat()
	var animationAccumulator: Float = 0.toFloat()

	var rotation: Float = 0.toFloat()
	var fixPosition: Boolean = false

	var flipX: Boolean = false
	var flipY: Boolean = false

	val size = intArrayOf(1, 1)

	var completed = false

	var animationStage = AnimationStage.INVALID
	var animationState: AnimationState

	var baseScale = floatArrayOf(1f, 1f)

	init
	{
		animationState = AnimationState()
		animationState.mode = mode

		this.colour = colour
	}

	val lifetime: Float
		get() = if (animation != null) animation!!.duration() else animationDelay * textures.size

	val remainingLifetime: Float
		get() = if (animation != null) animation!!.duration() - animation!!.time() else animationDelay * (textures.size - animationState.texIndex)

	override fun doUpdate(delta: Float): Boolean
	{
		if (repeatAccumulator > 0)
		{
			repeatAccumulator -= delta
		}

		var looped = false
		if (repeatAccumulator <= 0)
		{
			if (animationStage == AnimationStage.INVALID) animationStage = AnimationStage.START
			animationAccumulator += delta

			while (animationAccumulator >= animationDelay)
			{
				animationAccumulator -= animationDelay

				if (animationState.mode == AnimationMode.TEXTURE)
				{
					if (animation == null && animationState.texIndex == textures.size / 2)
					{
						animationStage = AnimationStage.MIDDLE
					}

					animationState.texIndex++
					if (animationState.texIndex >= textures.size)
					{
						animationState.texIndex = 0
						looped = true
						repeatAccumulator = repeatDelay
					}
				}
				else if (animationState.mode == AnimationMode.SHRINK)
				{
					animationState.isShrunk = !animationState.isShrunk
					looped = animationState.isShrunk
				}
				else if (animationState.mode == AnimationMode.SINE)
				{
					looped = true
				}
			}
		}

		if (animationState.mode == AnimationMode.SINE)
		{
			animationState.sinOffset = Math.sin(animationAccumulator / (animationDelay / (2 * Math.PI))).toFloat()
		}

		if (animation != null)
		{
			if (animationStage == AnimationStage.INVALID) animationStage = AnimationStage.START
			looped = animation!!.update(delta)

			if (animation!!.time() >= animation!!.duration() / 2f)
			{
				if (animation == null && animationState.texIndex == textures.size / 2)
				{
					animationStage = AnimationStage.MIDDLE
				}
			}

			if (looped)
			{
				animation!!.free()
				animation = null
			}
		}

		if (colourAnimation != null)
		{
			val looped = colourAnimation!!.update(delta)
			if (looped && colourAnimation!!.oneTime)
			{
				colourAnimation!!.free()
				colourAnimation = null
			}
		}

		if (looped)
		{
			animationStage = AnimationStage.END
		}

		if (!completed) completed = looped
		return looped
	}

	override fun doRender(batch: Batch, x: Float, y: Float, size: Float)
	{
		var scaleX = baseScale[0]
		var scaleY = baseScale[1]

		if (animation != null)
		{
			val scale = animation!!.renderScale()
			if (scale != null)
			{
				scaleX *= scale[0]
				scaleY *= scale[1]
			}
		}

		render(batch, x, y, size, size, scaleX, scaleY, animationState)
	}

	fun render(batch: Batch, x: Float, y: Float, width: Float, height: Float)
	{
		var scaleX = baseScale[0]
		var scaleY = baseScale[1]

		if (animation != null)
		{
			val scale = animation!!.renderScale()
			if (scale != null)
			{
				scaleX *= scale[0]
				scaleY *= scale[1]
			}
		}

		render(batch, x, y, width, height, scaleX, scaleY, animationState)
	}

	private fun render(batch: Batch, x: Float, y: Float, width: Float, height: Float, scaleX: Float, scaleY: Float, animationState: AnimationState)
	{
		val colour = if (colourAnimation != null) colourAnimation!!.renderColour()!! else if (animation?.renderColour() != null) animation!!.renderColour()!! else this.colour

		if (colour.a == 0f)
		{
			return
		}

		val oldCol = (batch as? HDRColourSpriteBatch)?.colour ?: tempCol.set(batch.color)

		val col = tempColour.set(oldCol)
		col *= colour
		(batch as? HDRColourSpriteBatch)?.setColor(col) ?: batch.setColor(col.r, col.g, col.b, col.a)

		drawTexture(batch, textures.items[animationState.texIndex], x, y, width, height, scaleX, scaleY, animationState)

		(batch as? HDRColourSpriteBatch)?.setColor(oldCol) ?: batch.setColor(oldCol.r, oldCol.g, oldCol.b, oldCol.a)
	}

	private fun drawTexture(batch: Batch, texture: TextureRegion, x: Float, y: Float, width: Float, height: Float, scaleX: Float, scaleY: Float, animationState: AnimationState)
	{
		var x = x
		var y = y
		var width = width
		var height = height

		width *= size[0]
		height *= size[1]

		if (drawActualSize)
		{
			val widthRatio = width / 32f
			val heightRatio = height / 32f

			val regionWidth = referenceSize ?: texture.regionWidth.toFloat()
			val regionHeight = referenceSize ?: texture.regionHeight.toFloat()

			val trueWidth = regionWidth * widthRatio
			val trueHeight = regionHeight * heightRatio

			val widthOffset = (trueWidth - width) / 2

			x -= widthOffset
			width = trueWidth
			height = trueHeight
		}

		if (animationState.mode == AnimationMode.SHRINK && animationState.isShrunk)
		{
			height *= 0.85f
		}
		else if (animationState.mode == AnimationMode.SINE)
		{
			y += height / 15f * animationState.sinOffset
		}

		if (rotation != 0f && fixPosition)
		{
			val offset = getPositionCorrectionOffsets(x, y, width / 2.0f, height / 2.0f, width, height, scaleX, scaleY, rotation)
			x -= offset.x
			y -= offset.y
		}

		if (batch is HDRColourSpriteBatch)
		{
			batch.draw(texture, x, y, width / 2.0f, height / 2.0f, width, height, scaleX, scaleY, rotation, flipX, flipY)
		}
		else
		{
			batch.draw(texture, x, y, width / 2.0f, height / 2.0f, width, height, scaleX, scaleY, rotation)
		}
	}

	val currentTexture: TextureRegion
		get() = textures.get(animationState.texIndex)

	override fun copy(): Sprite
	{
		val sprite = Sprite(fileName, animationDelay, textures, colour, animationState.mode, drawActualSize)
		sprite.referenceSize = referenceSize
		sprite.animation = animation?.copy()
		sprite.colourAnimation = colourAnimation?.copy() as? AbstractColourAnimation

		return sprite
	}

	class AnimationState
	{
		lateinit var mode: AnimationMode

		var texIndex: Int = 0
		var isShrunk: Boolean = false
		var sinOffset: Float = 0.toFloat()

		fun copy(): AnimationState
		{
			val `as` = AnimationState()

			`as`.mode = mode
			`as`.texIndex = texIndex
			`as`.isShrunk = isShrunk
			`as`.sinOffset = sinOffset

			return `as`
		}

		fun set(other: AnimationState)
		{
			mode = other.mode
			texIndex = other.texIndex
			isShrunk = other.isShrunk
			sinOffset = other.sinOffset
		}
	}

	companion object
	{

		private val tempColour = Colour()

		private val tempVec = Vector3()
		private val tempMat = Matrix3()

		fun getPositionCorrectionOffsets(x: Float, y: Float, originX: Float, originY: Float, width: Float, height: Float,
												 scaleX: Float, scaleY: Float, rotation: Float): Vector3
		{
			// bottom left and top right corner points relative to origin
			val worldOriginX = x + originX
			val worldOriginY = y + originY
			var fx = -originX
			var fy = -originY
			var fx2 = width - originX
			var fy2 = height - originY

			// scale
			if (scaleX != 1f || scaleY != 1f)
			{
				fx *= scaleX
				fy *= scaleY
				fx2 *= scaleX
				fy2 *= scaleY
			}

			// construct corner points, start from top left and go counter clockwise
			val p1x = fx
			val p1y = fy
			val p2x = fx
			val p2y = fy2
			val p3x = fx2
			val p3y = fy2
			val p4x = fx2
			val p4y = fy

			val x1: Float
			val y1: Float
			val x2: Float
			val y2: Float
			val x3: Float
			val y3: Float
			val x4: Float
			val y4: Float

			// rotate
			if (rotation != 0f)
			{
				val cos = MathUtils.cosDeg(rotation)
				val sin = MathUtils.sinDeg(rotation)

				x1 = cos * p1x - sin * p1y
				y1 = sin * p1x + cos * p1y

				x2 = cos * p2x - sin * p2y
				y2 = sin * p2x + cos * p2y

				x3 = cos * p3x - sin * p3y
				y3 = sin * p3x + cos * p3y

				x4 = x1 + (x3 - x2)
				y4 = y3 - (y2 - y1)
			} else
			{
				x1 = p1x
				y1 = p1y

				x2 = p2x
				y2 = p2y

				x3 = p3x
				y3 = p3y

				x4 = p4x
				y4 = p4y
			}

			tempVec.set(x1, y1, 0f)

			if (x2 < tempVec.x) tempVec.x = x2
			if (x3 < tempVec.x) tempVec.x = x3
			if (x4 < tempVec.x) tempVec.x = x4

			if (y2 < tempVec.y) tempVec.y = y2
			if (y3 < tempVec.y) tempVec.y = y3
			if (y4 < tempVec.y) tempVec.y = y4

			tempVec.x += worldOriginX
			tempVec.y += worldOriginY

			tempVec.x -= x
			tempVec.y -= y

			return tempVec
		}
	}
}
