package com.lyeeedar.Board.CompletionCondition

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Board.Grid
import com.lyeeedar.Board.LevelTheme
import com.lyeeedar.Board.Tile
import com.lyeeedar.Global
import com.lyeeedar.UI.SpriteWidget
import com.lyeeedar.Util.AssetManager

class CompletionConditionPlate : AbstractCompletionCondition()
{
	var remaining = 0
	lateinit var label: Label

	override fun attachHandlers(grid: Grid)
	{
		grid.onTurn += {
			remaining = grid.grid.count(Tile::hasPlate)
			label.setText("$remaining")
		}
	}

	override fun isCompleted(): Boolean = remaining == 0

	override fun parse(xml: XmlReader.Element)
	{
	}

	override fun createTable(skin: Skin, theme: LevelTheme): Table
	{
		val table = Table()

		val sprite = theme.plate.copy()
		label = Label("$remaining", Global.skin)

		table.add(SpriteWidget(sprite, 24f, 24f))
		table.add(label)

		return table
	}

	override fun getTextDescription(): String = "Break all the plates"
}