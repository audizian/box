package dev.idot.boxplugin

import org.bukkit.block.BlockFace

internal data class Bloc(val x: Int = 0, val y: Int = 0, val z: Int = 0, val face: BlockFace = BlockFace.NORTH, val top: Boolean = false) {
    fun rotate(cycles: Int): BlockFace = when(cycles % 4) {
        0 -> BlockFace.NORTH
        1 -> BlockFace.EAST
        2 -> BlockFace.SOUTH
        3 -> BlockFace.WEST
        else -> throw NullPointerException("Expression 'when(cycles % 4) { ...' must not be null")
    }
}