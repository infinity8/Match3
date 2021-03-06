package com.lyeeedar.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.XmlReader
import com.lyeeedar.Board.Level
import com.lyeeedar.Board.LevelTheme
import com.lyeeedar.Player.Player
import ktx.collections.get
import ktx.collections.set
import java.io.File

/**
 * Created by Philip on 28-Jul-16.
 */

class LevelProcessor
{
	val dungeons = ObjectMap<String, Array<String>>()

	init
	{
		findDungeons( File("World/Levels") )

		var output = "<Dungeons>\n"

		for (dungeon in dungeons)
		{
			output += "\t<${dungeon.key}>\n"

			for (path in dungeon.value)
			{
				output += "\t\t<Level>$path</Level>\n"
			}

			output += "\t</${dungeon.key}>\n"
		}

		output += "</Dungeons>"

		val outputHandle = FileHandle(File("World/Levels/LevelList.xml"))
		outputHandle.writeString(output, false)
	}

	private fun findDungeons(dir: File)
	{
		val contents = dir.listFiles() ?: return

		for (file in contents)
		{
			if (file.isDirectory)
			{
				dungeons[file.name] = Array()
				findFilesRecursive(file, file.name)
			}
		}
	}

	private fun findFilesRecursive(dir: File, dungeon: String)
	{
		val contents = dir.listFiles() ?: return

		for (file in contents)
		{
			if (file.isDirectory)
			{
				findFilesRecursive(file, dungeon)
			}
			else if (file.path.endsWith(".xml"))
			{
				try
				{
					parseXml(file.path, dungeon)
				}
				catch (e: Exception)
				{
					error("Failed to load '${file.path}'!\n${e.message}")
				}
			}
		}
	}

	fun parseXml( path: String, dungeon: String )
	{
		val xml = XmlReader().parse(Gdx.files.internal(path))

		if (xml.name != "Level") return

		// Test load the level
		val theme = LevelTheme.load(dungeon)
		val levels = Level.load(path.removePrefix("World\\Levels\\"))

		for (level in levels)
		{
			level.create(theme, Player())
		}

		if (!xml.getBooleanAttribute("IsInMapPool", true)) return

		var p = path
		p = p.replace("\\", "/")
		p = p.replace("World/Levels/", "")
		p = p.replace(".xml", "")

		dungeons[dungeon].add(p)
		System.out.println("Adding level $p")
	}


}