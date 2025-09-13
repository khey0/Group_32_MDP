package com.example.group_32_mdp

import android.util.Log

data class Car(
    var x: Int = -1,  // top-left x coordinate on grid
    var y: Int = -1,  // top-left y coordinate on grid
    var direction: Direction = Direction.NORTH,
    val width: Int = 2,  // 2x2 dimension
    val height: Int = 2,
    val leewayHead: Int = 1, // determines offset to front of car when turning forwards
    val leewaySide: Int = 1, // determine offset to sides of car when turning forwards
    val leewayBack: Int = 1, // determines offset to back of car when turning backwards
    val leewayBSide: Int = 1 // determine offset to sides of car when turning backwards
) {
    fun moveForward() {
        when (direction) {
            Direction.NORTH -> if (y < 19 && GridData.isAreaClear(x..x+width-1,y+1..y+1)) y += 1       // can't go past top
            Direction.SOUTH -> if (y > 1 && GridData.isAreaClear(x..x+width-1,y-height..y-height)) y -= 1                 // can't go below 0
            Direction.EAST  -> if (x < 18 && GridData.isAreaClear(x+height..x+height,y-width+1..y)) x += 1        // can't go past right edge
            Direction.WEST  -> if (x > 0 && GridData.isAreaClear(x-1..x-1,y-width+1..y)) x -= 1                 // can't go past left edge
        }
        Log.d("this","Car moved to ($x, $y)")
    }

    fun moveBackward() {
        when (direction) {
            Direction.NORTH -> if (y > 1 && GridData.isAreaClear(x..x+width-1,y-height..y)) y -= 1
            Direction.SOUTH -> if (y < 19 && GridData.isAreaClear(x..x+width-1,y+1..y+1)) y += 1
            Direction.EAST -> if (x > 0 && GridData.isAreaClear(x-1..x-1,y-width+1..y)) x -= 1
            Direction.WEST -> if (x < 18 && GridData.isAreaClear(x+height..x+height,y-width+1..y)) x += 1
        }
        Log.d("this","Car moved to ($x, $y)")
    }

    fun moveForwardLeft() {
        direction = when (direction) {
            Direction.NORTH -> {
                if (y + leewayHead < 20 && x - leewaySide > -1 &&
                    GridData.isAreaClear(x - leewaySide..x- leewaySide + 1,
                                         y + leewayHead - 1..y + leewayHead)) {
                    y += leewayHead
                    x -= leewaySide
                    Direction.WEST
                } else {direction}
            }
            Direction.WEST -> {
                if (y - leewaySide > 0 && x - leewayHead > -1 &&
                    GridData.isAreaClear(x-leewayHead..x-leewayHead+1,
                                         y-leewaySide-1..y-leewaySide)) {
                    y -= leewaySide
                    x -= leewayHead
                    Direction.SOUTH
                } else {direction}
            }
            Direction.SOUTH -> {
                if (y - leewayHead > 0 && x + leewaySide < 19 &&
                    GridData.isAreaClear(x+leewaySide..x+leewaySide+1,
                                         y-leewayHead-1..y-leewayHead)) {
                    y -= leewayHead
                    x += leewaySide
                    Direction.EAST
                } else {direction}
            }
            Direction.EAST -> {
                if (y+leewaySide < 20 && x+leewayHead < 19 &&
                    GridData.isAreaClear(x+leewayHead..x+leewayHead+1,
                                         y+leewaySide-1..y+leewaySide)) {
                    y += leewaySide
                    x += leewayHead
                    Direction.NORTH
                } else {direction}
            }
        }
        Log.d("this","Car moved to ($x, $y)")
    }

    fun moveBackwardLeft() {
        direction = when (direction) {
            Direction.NORTH -> {
                if (y-leewayBack > 0 && x-leewayBSide > -1 &&
                    GridData.isAreaClear(x-leewayBSide..x-leewayBSide+1,
                                         y-leewayBack-1..y-leewayBack)) {
                    y -= leewayBack
                    x -= leewayBSide
                    Direction.EAST
                } else {direction}
            }
            Direction.EAST -> {
                if (y+leewayBSide < 20 && x-leewayBack > -1 &&
                    GridData.isAreaClear(x-leewayBack..x-leewayBack+1,
                                         y+leewayBSide-1..y+leewayBSide)) {
                    y += leewayBSide
                    x -= leewayBack
                    Direction.SOUTH
                } else {direction}
            }
            Direction.SOUTH -> {
                if (y+leewayBack<20 && x+leewayBSide<19 &&
                    GridData.isAreaClear(x+leewayBSide..x+leewayBSide+1,
                                         y+leewayBack-1..y+leewayBack)) {
                    y += leewayBack
                    x += leewayBSide
                    Direction.WEST
                } else {direction}
            }
            Direction.WEST -> {
                if (y-leewayBSide > 0 && x+leewayBack < 19 &&
                    GridData.isAreaClear(x+leewayBack..x+leewayBack+1,
                                         y-leewayBSide-1..y-leewayBSide)) {
                    y -= leewayBSide
                    x += leewayBack
                    Direction.NORTH
                } else {direction}
            }
        }
        Log.d("this","Car moved to ($x, $y)")
    }

    fun moveForwardRight() {
        direction = when (direction) {
            Direction.NORTH -> {
                if (y + leewayHead < 20 && x + leewaySide < 19 &&
                    GridData.isAreaClear(x + leewaySide..x+leewaySide + 1,
                                         y + leewayHead - 1..y + leewayHead)) {
                    y += leewayHead
                    x += leewaySide
                    Direction.EAST
                } else {direction}
            }
            Direction.WEST -> {
                if (y + leewaySide < 20 && x - leewayHead > -1 &&
                    GridData.isAreaClear(x-leewayHead..x-leewayHead+1,
                                         y+leewaySide-1..y+leewaySide)) {
                    y += leewaySide
                    x -= leewayHead
                    Direction.NORTH
                } else {direction}
            }
            Direction.SOUTH -> {
                if (y - leewayHead > 0 && x - leewaySide > -1 &&
                    GridData.isAreaClear(x-leewaySide..x-leewaySide+1,
                                         y-leewayHead-1..y-leewayHead)) {
                    y -= leewayHead
                    x -= leewaySide
                    Direction.WEST
                } else {direction}
            }
            Direction.EAST -> {
                if (y-leewaySide > 0 && x+leewayHead < 19 &&
                    GridData.isAreaClear(x+leewayHead..x+leewayHead+1,
                                         y-leewaySide-1..y-leewaySide)) {
                    y -= leewaySide
                    x += leewayHead
                    Direction.SOUTH
                } else {direction}
            }
        }
        Log.d("this","Car moved to ($x, $y)")
    }

    fun moveBackwardRight() {
        direction = when (direction) {
            Direction.NORTH -> {
                if (y-leewayBack > 0 && x+leewayBSide < 19 &&
                    GridData.isAreaClear(x+leewayBSide..x+leewayBSide+1,
                                         y-leewayBack-1..y-leewayBack)) {
                    y -= leewayBack
                    x += leewayBSide
                    Direction.WEST
                } else {direction}
            }
            Direction.EAST -> {
                if (y-leewayBSide > 0 && x-leewayBack > -1 &&
                    GridData.isAreaClear(x-leewayBack..x-leewayBack+1,
                                         y-leewayBSide-1..y-leewayBSide)) {
                    y -= leewayBSide
                    x -= leewayBack
                    Direction.NORTH
                } else {direction}
            }
            Direction.SOUTH -> {
                if (y+leewayBack<20 && x-leewayBSide>-1 &&
                    GridData.isAreaClear(x-leewayBSide..x-leewayBSide+1,
                                         y+leewayBack-1..y+leewayBack)) {
                    y += leewayBack
                    x -= leewayBSide
                    Direction.EAST
                } else {direction}
            }
            Direction.WEST -> {
                if (y+leewayBSide < 20 && x+leewayBack < 19 &&
                    GridData.isAreaClear(x+leewayBack..x+leewayBack+1,
                                         y+leewayBSide-1..y+leewayBSide)) {
                    y += leewayBSide
                    x += leewayBack
                    Direction.SOUTH
                } else {direction}
            }
        }
        Log.d("this","Car moved to ($x, $y)")
    }
}