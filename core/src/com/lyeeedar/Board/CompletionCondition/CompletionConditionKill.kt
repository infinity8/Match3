package com.lyeeedar.Board.CompletionCondition

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Board.Grid
import com.lyeeedar.Board.LevelTheme
import com.lyeeedar.Board.Monster
import com.lyeeedar.Board.MonsterDesc
import com.lyeeedar.Global
import com.lyeeedar.UI.SpriteWidget
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour
import ktx.collections.set


class CompletionConditionKill() : AbstractCompletionCondition()
{
	val tick = AssetManager.loadSprite("Oryx/uf_split/uf_interface/uf_interface_680", colour = Colour(Color.FOREST))

	var monsters = Array<Monster>()
	var monsterMap = ObjectMap<MonsterDesc, Int>()

	val table = Table()

	override fun attachHandlers(grid: Grid)
	{
		for (tile in grid.grid)
		{
			if (tile.monster != null)
			{
				val monster = tile.monster!!
				if (!monsters.contains(monster, true))
				{
					monsters.add(monster)
				}
			}
		}

		grid.onDamaged += fun(c) : Boolean {
			rebuildWidget()
			return false
		}
	}

	override fun isCompleted(): Boolean = monsters.filter { it.hp > 0 }.count() == 0

	override fun parse(xml: XmlReader.Element)
	{
	}

	override fun createTable(skin: Skin, theme: LevelTheme): Table
	{
		rebuildWidget()

		return table
	}

	fun rebuildWidget()
	{
		table.clear()

		monsterMap.clear()

		for (monster in monsters)
		{
			if (!monsterMap.containsKey(monster.desc))
			{
				monsterMap[monster.desc] = 0
			}

			var count = monsterMap[monster.desc]
			if (monster.hp > 0)
			{
				count++
			}
			monsterMap[monster.desc] = count
		}

		for (monster in monsterMap)
		{
			val sprite = monster.key.sprite.copy()
			val count = monster.value

			table.add(SpriteWidget(sprite, 24f, 24f)).padLeft(5f)

			if (count == 0)
			{
				table.add(SpriteWidget(tick, 24f, 24f))
			}
			else
			{
				table.add(Label(" x $count", Global.skin))
			}
		}
	}

	override fun getTextDescription(): String = "Kill all the monsters"
}