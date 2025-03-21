package dev.idot.boxplugin

import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockFace.EAST
import org.bukkit.block.BlockFace.SOUTH
import org.bukkit.block.BlockFace.WEST
import org.bukkit.block.data.*
import org.bukkit.material.Directional as MaterialDirection
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlin.text.split

class BoxPlugin : JavaPlugin() {
    override fun onEnable() {
        instance = this
        val boxCommand = getCommand("box") ?: return logger.warning("/box command returned null. Is the plugin.yml broken? Disabling plugin...")
        boxCommand.executor = boxExecutor
        boxCommand.tabCompleter = boxTabCompleter
    }

    @Suppress("NOTHING_TO_INLINE")
    companion object {
        private lateinit var instance: BoxPlugin

        private val CMD_USAGE: String = "&dUsage: &f/box <player> [block]".color()
        private val NO_PERMISSION = "&cYou do not have permission to use this command.".color()
        private val SPECIFY_PLAYER = "&cYou must specify a player.".color()

        private val byteStringArray = Array(16) { it.toString() }
        private val boolStringArray = arrayOf("true", "false")
        private val rotationStringArray = BlockFace.entries.map { it.name.lowercase() }.toTypedArray()

        private val boxCoordinates = arrayOf(
            Bloc(y = -1),
            Bloc(z =-1), Bloc(x = 1, face = EAST), Bloc(z = 1, face = SOUTH), Bloc(x = -1, face = WEST),
            Bloc(y = 1, z =-1), Bloc(x = 1, y = 1, face = EAST), Bloc(y = 1, z = 1, face = SOUTH), Bloc(x = -1, y = 1, face = WEST),
            Bloc(y = 2, face = SOUTH)
        )

        private val commandFlags = arrayOf("-s")

        private val safeBlocks = Material.entries.mapNotNull { mat ->
            if (mat.isBlock && mat.isSolid) mat.key.key.lowercase() else null
        }

        private val unsafeBlocks = Material.entries.mapNotNull { mat ->
            if (mat.isBlock) mat.key.key.lowercase() else null
        }

        private inline fun getBlocks(sender: CommandSender) =
            if (sender.hasPermission("box.unsafeblocks")) unsafeBlocks else safeBlocks

        private val boxExecutor = CommandExecutor cmd@ { sender, _, _, args ->
            fun CommandSender.returnMessage(string: String): Boolean {
                sendMessage(string.color())
                return true
            }

            if (!sender.hasPermission("box.use"))
                return@cmd sender.returnMessage(NO_PERMISSION)

            val args = args.toMutableList()
            val playerName = args.removeFirstOrNull()
            if (playerName == null){
                sender.sendMessage(SPECIFY_PLAYER)
                return@cmd sender.returnMessage(CMD_USAGE)
            }

            val player = Bukkit.getPlayer(playerName)
                ?: return@cmd sender.returnMessage("&cPlayer '$playerName' is not found.")

            if (!player.isOnline || !(sender as Player).canSee(player))
                return@cmd sender.returnMessage("&cPlayer '$playerName' is not online.")

            val material = args.removeFirstOrNull()?.generateMaterial(!sender.hasPermission("box.unsafeblock"))?.onFailure {
                return@cmd sender.returnMessage(it.message ?: "&cAn unchecked error has occurred")
            }?.getOrNull() ?: Material.GLASS

            instance.server.scheduler.runTask(instance) {
                val location = player.location.clone()
                location.run {
                    x = blockX.toDouble() + 0.5
                    y = blockY.toDouble()
                    z = blockZ.toDouble() + 0.5
                    for (bl in boxCoordinates) {
                        player.world.getBlockAt(blockX + bl.x, blockY + bl.y, blockZ + bl.z).run {
                            if (type != Material.AIR) return@run
                            val copy = material
                            type = copy
                            (blockData as? MaterialDirection)?.run {
                                setFacingDirection(bl.face)
                                blockData = this as BlockData
                            }
                            state.update(true)
                        }
                    }
                }
                player.teleport(location)
                if (!args.contains("-s")) {
                    sender.sendMessage("&aYou have put &f${player.name} &ain a box".color())
                    player.sendMessage("&aYou have been put in a box by &f${sender.name}".color())
                }
            }
            true
        }

        private val boxTabCompleter = TabCompleter tab@ { sender, _, _, args -> when (args.lastIndex) {
            0 -> instance.server.onlinePlayers.stringFilter(args[0]) { if ((sender as Player).canSee(it)) it.name else null }
            1 -> {
                val blockList = getBlocks(sender)
                val query = args[1]
                val queryData = query.split('[', limit = 2).let { it.first() to it.getOrNull(1) }
                if (queryData.first !in blockList) return@tab blockList.stringFilter(query)

                val blockData = Material.matchMaterial(queryData.first)?.createBlockData()
                    ?: return@tab listOf("unknown-block")

                val data = queryData.second?.split(',')
                val existing = data?.map {
                    it.split('=', limit = 2).let { it[0] to it.getOrNull(1)?.trim() }
                } ?: emptyList()

                val properties = mutableListOf<String>()
                fun addProperty(key: String, values: Array<String>) {
                     if (existing.find { it.first == key } == null)
                         properties.add("$key=")
                     else if (existing.find { it == key to it }?.second == null)
                         properties.addAll(values.map { "$key=$it" })
                }

                if (blockData is Bisected) addProperty("half", arrayOf("top", "bottom"))
                if (blockData is Directional) addProperty("facing", arrayOf("north", "east", "south", "west"))
                if (blockData is Levelled) addProperty("level", byteStringArray)
                if (blockData is Lightable) addProperty("lit", boolStringArray)
                if (blockData is Openable) addProperty("open", boolStringArray)
                if (blockData is Rotatable) addProperty("rotation", rotationStringArray)
                if (blockData is Waterlogged) addProperty("waterlogged", boolStringArray)


                //Determine cursor
                val last = data?.lastOrNull()?.trim() ?: ""
                val curKey = last.substringBefore("=").trim()
                val curVal = last.substringAfter("=", "").trim()

                val rebuiltProperties = existing.joinToString(",") { (key, value) ->
                    if (key == curKey && last.contains("=")) {
                        "$key=$curVal"
                    } else if (value != null) {
                        "$key=$value" // Rebuild previous values.
                    } else {
                        key // Just the key, no value
                    }
                }
                properties.stringFilter(last).map {
                    val final = rebuiltProperties.let {
                        if (it.isNotEmpty()) "$it,$it" else it
                    }
                    "${queryData.first}[$final]"
                }.toList()
            }
            else -> {
                commandFlags.drop(2).stringFilter(args.last())
            }
        }   }

        private fun String.generateMaterial(allowUnsafeBlocks: Boolean): Result<Material> {
            fun failure(string: String): Result<Material> = Result.failure(IllegalArgumentException(string))

            val query = split('[', ']', limit = 3)
            var material = query.first().let { material ->
                Material.matchMaterial(material) ?: return failure("&cInvalid material &f'$material'")
            }

            if (!material.isBlock)
                return failure("&f'${material.key.key}' &cis not a valid block")
            if (allowUnsafeBlocks && !material.isSolid)
                return failure("&cYou cannot use &f'${material.key.key}'")

            query.getOrNull(1)?.let { modifier ->
                val data = mutableMapOf<String, String>()
                for (string in modifier.split(',')) {
                    val mod = string.split('=', limit = 2)
                    val key = mod.firstOrNull()
                    val value = mod.lastOrNull()
                    if (key.isNullOrEmpty() || value.isNullOrEmpty()) return failure("&cInvalid modifier: &f$mod")
                    data[key] = value
                }

                val blockData = material.createBlockData()
                if (blockData is Bisected) {
                    data["half"]?.let { half ->
                        blockData.half =
                            match<Bisected.Half>(half) ?: return failure("&cInvalid half: &f$half")
                    }
                }
                if (blockData is Directional) {
                    data["facing"]?.let { facing ->
                        val match = match<BlockFace>(facing)
                        if (match in blockData.faces) blockData.facing = match
                        else return failure("&cInvalid face: &f$match")
                    }
                }
                if (blockData is Levelled) {
                    data["level"]?.let { level ->
                        val match = level.toIntOrNull() ?: return failure("&cInvalid level &f$level")
                        if (match in 0..blockData.maximumLevel) blockData.level = match
                        else return failure("&cInvalid level: &f$match &cis not between &f0-${blockData.maximumLevel}")
                    }
                }
                if (blockData is Lightable) {
                    data["lit"]?.let { light ->
                        blockData.isLit =
                            light.toBooleanStrictOrNull() ?: return failure("&cInvalid state: &f$light")
                    }
                }
                if (blockData is Openable) {
                    data["open"]?.let { open ->
                        blockData.isOpen =
                            open.toBooleanStrictOrNull() ?: return failure("&cInvalid state: &f$open")
                    }
                }
                if (blockData is Rotatable) {
                    data["rotation"]?.let { rotation ->
                        blockData.rotation =
                            match<BlockFace>(rotation) ?: return failure("&cInvalid face: &f$rotation")
                    }
                }
                if (blockData is Waterlogged) {
                    data["waterlogged"]?.let { waterlogged ->
                        blockData.isWaterlogged =
                            waterlogged.toBooleanStrictOrNull() ?: return failure("&cInvalid state &f$waterlogged")
                    }
                }
            }
            return Result.success(material)
        }

        private fun String.color(): String = ChatColor.translateAlternateColorCodes('&', this)

        private fun Iterable<String>.stringFilter(query: String): List<String> {
            if (query.isBlank()) return toList()
            val result = mutableListOf<String>()
            for (it in this) {
                if (query.length == 1) {
                    if (it.startsWith(query, true)) result.add(it)
                } else if (it.contains(query, true)) result.add(it)
            }
            return result
        }

        private inline fun <T> Iterable<T>.stringFilter(query: String, task: (T) -> String?): List<String> {
            if (query.isBlank()) return mapNotNull { task(it) }
            val result = mutableListOf<String>()
            for (iter in this) {
                val it = task(iter) ?: continue
                if (query.length == 1) {
                    if (it.startsWith(query, true)) result.add(it)
                } else if (it.contains(query, true)) result.add(it)
            }
            return result
        }

        private inline fun <reified T : Enum<T>> match(string: String?): T? {
            string ?: return null
            return enumValues<T>().find { it.name.equals(string, ignoreCase = true) }
        }
    }
}