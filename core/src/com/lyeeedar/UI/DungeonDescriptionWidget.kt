package com.lyeeedar.UI

import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.lyeeedar.Board.LevelTheme
import com.lyeeedar.Global
import com.lyeeedar.MainGame
import com.lyeeedar.Map.DungeonMap
import com.lyeeedar.Map.Generators.HubGenerator
import com.lyeeedar.Map.WorldDungeon
import com.lyeeedar.Player.Player
import com.lyeeedar.Player.PlayerData
import com.lyeeedar.Screens.MapScreen
import com.lyeeedar.Util.AssetManager

/**
 * Created by Philip on 02-Aug-16.
 */

class DungeonDescriptionWidget(val dungeon: WorldDungeon, val playerData: PlayerData, val mapWidget: Actor, val button: Actor) : FullscreenTable()
{
	val table = Table()

	init
	{
		table.background = NinePatchDrawable(NinePatch(AssetManager.loadTextureRegion("Sprites/GUI/background.png"), 24, 24, 24, 24))

		this.add(table).expand().fill().pad(15f)

		// close button
		val closeButton = Button(Global.skin, "close")
		closeButton.setSize(24f, 24f)
		closeButton.addClickListener({ remove() })
		table.add(closeButton).width(24f).height(24f).expand().top().right()
		table.row()
		table.add(Label(dungeon.name, Global.skin, "title")).expandX().center().pad(30f)
		table.row()

		val descLabel = Label(dungeon.description, Global.skin)
		descLabel.setWrap(true)
		table.add(descLabel).expandX().fillX()
		table.row()
		table.add(Seperator(Global.skin)).expand().fillX().padTop(30f)
		table.row()
		var completionString = "Completion: " + (((dungeon.progression.toFloat()) / dungeon.progressionQuests.size.toFloat()+1) * 100f).toInt() + "%"
		if (!Global.release) completionString += " (" + dungeon.progression + "/" + (dungeon.progressionQuests.size+1) + ")"
		table.add(Label(completionString, Global.skin)).pad(30f)
		table.row()
		table.add(Seperator(Global.skin)).expand().fillX()
		table.row()
		table.add(Label("Objective", Global.skin, "title")).pad(30f)
		table.row()

		val objective = dungeon.getObjective()

		val missionLabel = Label(objective.getDescription(), Global.skin)
		missionLabel.setWrap(true)
		table.add(missionLabel).expandX().fillX().center()
		table.row()

		val embarkButton = TextButton("Embark", Global.skin)
		embarkButton.addClickListener {

			val theme = LevelTheme.load(dungeon.theme)
			var map: DungeonMap

			while (true)
			{
				val generator = HubGenerator(MathUtils.random(Long.MAX_VALUE))
				map = generator.generate(theme, 8, dungeon.progression, objective, dungeon.name)
				if (generator.numRoomsToSpawn <= 0) break
			}

			MapScreen.instance.setMap(map, Player(playerData), dungeon)
			Global.game.switchScreen(MainGame.ScreenEnum.MAP)

			remove()
			mapWidget.remove()
			button.remove()
		}
		table.add(embarkButton).expandX().center().pad(30f).width(100f)

		table.add(Table()).expand().fill() // padding
	}
}
