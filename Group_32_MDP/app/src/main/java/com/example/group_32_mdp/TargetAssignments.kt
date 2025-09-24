package com.example.group_32_mdp

/**
 * Stores the latest TARGET assignments sent from RPi: obstacleNumber -> targetId
 * UI can observe/consult this when drawing C9 overlays.
 */
object TargetAssignments {
	private val obstacleToTarget: MutableMap<Int, Int> = mutableMapOf()

	fun setTarget(obstacleNumber: Int, targetId: Int) {
		obstacleToTarget[obstacleNumber] = targetId
	}

	fun getTargetId(obstacleNumber: Int): Int? = obstacleToTarget[obstacleNumber]

	fun clear() {
		obstacleToTarget.clear()
	}

	fun asMapCopy(): Map<Int, Int> = obstacleToTarget.toMap()
}


