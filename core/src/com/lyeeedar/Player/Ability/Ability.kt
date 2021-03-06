package com.lyeeedar.Player.Ability

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Board.Grid
import com.lyeeedar.Board.Tile
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Screens.GridScreen
import com.lyeeedar.UI.GridWidget
import com.lyeeedar.UI.PowerBar
import com.lyeeedar.UI.Unlockable
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Future
import com.lyeeedar.Util.getRotation
import com.lyeeedar.Util.iterator
import ktx.collections.set

/**
 * Created by Philip on 20-Jul-16.
 */

class Ability() : Unlockable()
{
	var hitEffect: ParticleEffect? = null
	var flightEffect: ParticleEffect? = null

	var cost: Int = 2

	var targets = 1
	var targetter: Targetter = Targetter(Targetter.Type.ORB)
	var permuter: Permuter = Permuter(Permuter.Type.SINGLE)
	var effect: Effect = Effect(Effect.Type.TEST)
	val data = ObjectMap<String, String>()

	val selectedTargets = Array<Tile>()

	fun activate(grid: Grid)
	{
		PowerBar.instance.pips -= cost

		val finalTargets = Array<Tile>()

		if (permuter.type == Permuter.Type.RANDOM)
		{
			for (t in permuter.permute(grid.tile(grid.width/2, grid.height/2)!!, grid, data))
			{
				if (!selectedTargets.contains(t, true))
				{
					selectedTargets.add(t)
				}
			}
		}

		val selectedDelays = ObjectMap<Tile, Float>()

		for (target in selectedTargets)
		{
			if (permuter.type == Permuter.Type.RANDOM)
			{
				finalTargets.add(target)
			}
			else
			{
				for (t in permuter.permute(target, grid, data))
				{
					if (!finalTargets.contains(t, true))
					{
						finalTargets.add(t)
					}
				}
			}

			var delay = 0f
			if (flightEffect != null)
			{
				val fs = flightEffect!!.copy()

				val p1 = GridScreen.instance.playerPortrait.localToStageCoordinates(Vector2())
				val p2 = GridWidget.instance.pointToScreenspace(target)

				p1.scl(1f / GridScreen.instance.grid!!.ground.tileSize)
				p2.scl(1f / GridScreen.instance.grid!!.ground.tileSize)

				val dist = p1.dst(p2)

//				if (fs.moveType == ParticleEffect.MoveType.Leap)
//				{
//					val animDuration = (0.25f + dist * 0.025f) * fs.moveSpeed
//
//					val path = arrayOf(p1, p2)
//					for (point in path)
//					{
//						point.x -= path.last().x
//						point.y -= path.last().y
//					}
//
//					fs.animation = LeapAnimation.obtain().set(animDuration, path, 1f + dist * 0.25f)
//					fs.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
//				}
//				else
//				{
					fs.animation = MoveAnimation.obtain().set(dist, arrayOf(p1, p2), Interpolation.linear)
//				}

				fs.rotation = getRotation(p1, p2)
				delay += fs.lifetime

				target.effects.add(fs)
			}

			selectedDelays[target] = delay
		}

		for (target in finalTargets)
		{
			val closest = selectedTargets.minBy { it.dist(target) }!!
			val dst = closest.dist(target)

			Future.call(
					{
						var delay = 0.0f
						if (hitEffect != null)
						{
							val hs = hitEffect!!.copy()
							hs.renderDelay = delay + 0.1f * dst
							delay += hs.lifetime * 0.6f

							target.effects.add(hs)
						}

						effect.apply(target, grid, delay, data)
					}, selectedDelays[closest] - 0.05f)

		}

		selectedTargets.clear()
	}

	override fun parse(xml: XmlReader.Element, resources: ObjectMap<String, XmlReader.Element>)
	{
		val dataEl = xml.getChildByName("EffectData")

		val hitEffectData = dataEl.getChildByName("HitEffect")
		if (hitEffectData != null) hitEffect = AssetManager.loadParticleEffect(hitEffectData)
		val flightEffectData = dataEl.getChildByName("FlightEffect")
		if (flightEffectData != null) flightEffect = AssetManager.loadParticleEffect(flightEffectData)

		cost = dataEl.getInt("Cost", 1)

		val effectDesc = dataEl.get("Effect")
		val split = effectDesc.toUpperCase().split(",")

		targets = split[0].toInt()
		targetter = Targetter(Targetter.Type.valueOf(split[1]))
		permuter = Permuter(Permuter.Type.valueOf(split[2]))
		effect = Effect(Effect.Type.valueOf(split[3]))

		val dEl = dataEl.getChildByName("Data")
		if (dEl != null)
		{
			for (el in dEl)
			{
				data[el.name.toUpperCase()] = el.text.toUpperCase()
			}
		}
	}
}