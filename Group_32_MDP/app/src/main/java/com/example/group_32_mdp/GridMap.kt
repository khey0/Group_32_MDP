package com.example.group_32_mdp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

class GridMap(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val gridLinePaint = Paint()
    private val cellPaint = Paint()
    private val xTextPaint = Paint()
    private val yTextPaint = Paint()

    private val COL = 20
    private val ROW = 20
    private var cellSize: Float = 0f

    // Reserve space for axis labels (px)
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val axisMarginPx = 24f * density

    // Limit grid to avoid spanning full width (fraction of available width)
    private val maxWidthFraction = 0.85f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        initPaints()

        val rawAvailableWidth = width - paddingLeft - paddingRight
        val availableWidth = rawAvailableWidth - axisMarginPx // reserve left margin for Y labels
        val availableHeight = height - paddingTop - paddingBottom - axisMarginPx // reserve bottom for X labels

        // Constrain width usage so grid doesn't span entire width
        val widthCap = availableWidth * maxWidthFraction
        val candidateSide = min(availableHeight, widthCap)

        // Use integer-rounded cell size to avoid cumulative float error so every line shows
        cellSize = floor(candidateSide / COL)
        val gridSide = cellSize * COL

        // Origin: shift right by left axis margin; center remaining space
        val extraSpaceX = availableWidth - gridSide
        val originX = paddingLeft + axisMarginPx + maxOf(0f, extraSpaceX / 2f)
        val originY = paddingTop.toFloat()

        // Draw cells first
        for (x in 0 until COL) {
            for (y in 0 until ROW) {
                val left = originX + x * cellSize
                val top = originY + y * cellSize
                val right = left + cellSize
                val bottom = top + cellSize
                canvas.drawRect(left, top, right, bottom, cellPaint)
            }
        }

        // Draw grid lines pixel-aligned so none disappear
        val endX = originX + gridSide
        val endY = originY + gridSide
        for (i in 0..COL) {
            val xPos = originX + i * cellSize
            val px = round(xPos) + 0.5f
            canvas.drawLine(px, originY, px, endY, gridLinePaint)
        }
        for (i in 0..ROW) {
            val yPos = originY + i * cellSize
            val py = round(yPos) + 0.5f
            canvas.drawLine(originX, py, endX, py, gridLinePaint)
        }

        // X-axis numbers 0-19 at bottom (centered per cell)
        for (x in 0 until COL) {
            val cx = originX + x * cellSize + cellSize / 2f
            canvas.drawText(x.toString(), cx, endY + 12f * density, xTextPaint)
        }
        // Y-axis numbers on the left (0 at bottom, 19 at top)
        for (y in 0 until ROW) {
            val label = (ROW - 1 - y).toString()
            val cy = originY + y * cellSize + cellSize / 2f + 3f
            val labelX = originX - 6f * density
            canvas.drawText(label, labelX, cy, yTextPaint)
        }
    }

    private fun initPaints() {
        // White grid lines with 1px-aligned stroke
        gridLinePaint.color = Color.WHITE
        gridLinePaint.style = Paint.Style.STROKE
        gridLinePaint.strokeWidth = 1f
        gridLinePaint.isAntiAlias = false

        // Darker red tiles
        cellPaint.color = Color.parseColor("#C62828")
        cellPaint.style = Paint.Style.FILL

        // X labels centered
        xTextPaint.color = Color.BLACK
        xTextPaint.textSize = 12f * scaledDensity
        xTextPaint.textAlign = Paint.Align.CENTER
        xTextPaint.isAntiAlias = true

        // Y labels right-aligned near the grid left edge
        yTextPaint.color = Color.BLACK
        yTextPaint.textSize = 12f * scaledDensity
        yTextPaint.textAlign = Paint.Align.RIGHT
        yTextPaint.isAntiAlias = true
    }
}
