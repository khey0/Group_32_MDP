package com.example.group_32_mdp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

class GridMap(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val gridLinePaint = Paint()
    private val cellPaint = Paint()
    private val xTextPaint = Paint()
    private val yTextPaint = Paint()
    private val obstaclePaint = Paint()
    private val obstacleTextPaint = Paint()
    private val directionPaint = Paint()

    private val COL = 20
    private val ROW = 20
    private var cellSize: Float = 0f

    // Reserve space for axis labels (px)
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val axisMarginPx = 24f * density

    // Limit grid to avoid spanning full width (fraction of available width)
    private val maxWidthFraction = 0.80f

    // Obstacle management
    private var nextObstacleId = 1
    private var isObstacleMode = false
    private var isDragMode = false
    private var isEditMode = false
    private var draggedObstacle: Obstacle? = null
    private var selectedObstacle: Obstacle? = null
    private var isDragging: Boolean = false
    private var dragTouchX: Float = 0f
    private var dragTouchY: Float = 0f

    // Car related variables
    private var isSettingStart = false
    private var setStartButton: Button? = null
    private var carBitmap: Bitmap? = null

    // Grid origin coordinates
    private var originX: Float = 0f
    private var originY: Float = 0f

    // Callback interfaces
    interface ObstacleInteractionListener {
        fun onObstacleCreated(obstacle: Obstacle)
        fun onObstacleMoved(obstacle: Obstacle)
        fun onObstacleEditRequested(obstacle: Obstacle)
    }

    private var obstacleListener: ObstacleInteractionListener? = null

    fun setObstacleInteractionListener(listener: ObstacleInteractionListener) {
        this.obstacleListener = listener
    }

    fun setObstacleMode(enabled: Boolean) {
        isObstacleMode = enabled
        isDragMode = false
        isEditMode = false
        isSettingStart = false
        invalidate()
    }

    fun setDragMode(enabled: Boolean) {
        isDragMode = enabled
        isObstacleMode = false
        isEditMode = false
        isSettingStart = false
        invalidate()
    }

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        isObstacleMode = false
        isDragMode = false
        isSettingStart = false
        invalidate()
    }

    fun addObstacle(x: Int, y: Int): Obstacle {
        val obstacle = Obstacle(nextObstacleId++, x, y)
        GridData.setObstacle(x, y, obstacle.id, obstacle.direction)
        obstacleListener?.onObstacleCreated(obstacle)
        invalidate()
        return obstacle
    }

    fun updateObstacle(obstacle: Obstacle) {
        GridData.setObstacle(obstacle.x, obstacle.y, obstacle.id, obstacle.direction)
        obstacleListener?.onObstacleCreated(obstacle)
        invalidate()
    }

    fun removeObstacle(obstacle: Obstacle) {
        GridData.removeObstacle(obstacle.x, obstacle.y)
        invalidate()
    }

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
        // Pin grid as far left as possible while preserving Y-axis label margin
        originX = paddingLeft + axisMarginPx
        originY = paddingTop.toFloat()

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

        // Draw obstacles
        drawObstacles(canvas)
        // Draw Car
        drawCar(canvas)


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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Compute desired size so the view wraps just the grid plus axis margins
        val specWidth = MeasureSpec.getSize(widthMeasureSpec)
        val specHeight = MeasureSpec.getSize(heightMeasureSpec)
        val specWidthMode = MeasureSpec.getMode(widthMeasureSpec)
        val specHeightMode = MeasureSpec.getMode(heightMeasureSpec)

        val paddingW = paddingLeft + paddingRight
        val paddingH = paddingTop + paddingBottom

        val availableWidth = (if (specWidthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else specWidth) - paddingW
        val availableHeight = (if (specHeightMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else specHeight) - paddingH

        // Reserve margins for labels
        val usableWidth = (availableWidth - axisMarginPx).coerceAtLeast(0f)
        val usableHeight = (availableHeight - axisMarginPx).coerceAtLeast(0f)

        // Constrain width usage so grid doesn't span entire width
        val widthCap = usableWidth * maxWidthFraction
        val candidateSide = min(usableHeight, widthCap)
        val desiredCell = if (candidateSide > 0) floor(candidateSide / COL) else 0f
        val gridSide = (desiredCell * COL).coerceAtLeast(0f)

        val desiredWidth = (axisMarginPx + gridSide + paddingW).toInt()
        val desiredHeight = (axisMarginPx + gridSide + paddingH).toInt()

        val measuredW = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredH = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredW, measuredH)
    }

    private fun drawObstacles(canvas: Canvas) {
        // Draw obstacles from GridData
        for (y in 0 until ROW) {
            for (x in 0 until COL) {
                val cell = GridData.getCell(x, y)
                if (cell?.hasObstacle == true) {
                    // Hide the original while dragging the same obstacle
                    if (isDragging && draggedObstacle != null && draggedObstacle!!.x == x && draggedObstacle!!.y == y) {
                        continue
                    }
                    val left = originX + x * cellSize
                    val top = originY + y * cellSize
                    val right = left + cellSize
                    val bottom = top + cellSize

                    // Draw obstacle (black square)
                    canvas.drawRect(left, top, right, bottom, obstaclePaint)

                    // Draw obstacle number
                    val textX = left + cellSize / 2f
                    val textY = top + cellSize / 2f + obstacleTextPaint.textSize / 3f
                    canvas.drawText(cell.obstacleId.toString(), textX, textY, obstacleTextPaint)

                    // Draw direction indicator (yellow border on the specified side)
                    drawDirectionIndicator(canvas, cell.direction, left, top, right, bottom)
                }
            }
        }

        // Draw floating obstacle following the finger while dragging
        if (isDragging && draggedObstacle != null) {
            val left = dragTouchX - cellSize / 2f
            val top = dragTouchY - cellSize / 2f
            val right = left + cellSize
            val bottom = top + cellSize

            // Draw dragged obstacle with slight transparency
            val draggedPaint = Paint(obstaclePaint)
            draggedPaint.alpha = 160
            canvas.drawRect(left, top, right, bottom, draggedPaint)

            // Draw obstacle number
            val textX = left + cellSize / 2f
            val textY = top + cellSize / 2f + obstacleTextPaint.textSize / 3f
            canvas.drawText(draggedObstacle!!.id.toString(), textX, textY, obstacleTextPaint)

            // Draw direction indicator
            drawDirectionIndicator(canvas, draggedObstacle!!.direction, left, top, right, bottom)
        }
    }

    private fun drawCar(canvas: Canvas) {
        GridData.getCar()?.let { c ->
            carBitmap?.let { bitmap ->
                val drawY = ROW - 1 - c.y // invert Y for canvas drawing
                val left = originX + c.x * cellSize
                val top = originY + drawY * cellSize
                val targetRect = android.graphics.RectF(
                    left,
                    top,
                    left + cellSize * 2f,
                    top + cellSize * 2f
                )

                val matrix = android.graphics.Matrix()

                // Step 1: rotate around bitmap center
                val angle = when (c.direction) {
                    Direction.NORTH -> 0f
                    Direction.EAST  -> 90f
                    Direction.SOUTH -> 180f
                    Direction.WEST  -> 270f
                }
                val centerX = bitmap.width / 2f
                val centerY = bitmap.height / 2f
                matrix.postRotate(angle, centerX, centerY)

                // Step 2: map the rotated bitmap bounds
                val rotatedBounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                matrix.mapRect(rotatedBounds)

                // Step 3: scale so rotated bitmap fits 2×2 cells
                val scaleX = targetRect.width() / rotatedBounds.width()
                val scaleY = targetRect.height() / rotatedBounds.height()
                matrix.postScale(scaleX, scaleY, 0f, 0f)

                // Step 4: re-map bounds after scale
                val finalBounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                matrix.mapRect(finalBounds)

                // Step 5: translate so final bounds top-left aligns with targetRect
                val dx = targetRect.left - finalBounds.left
                val dy = targetRect.top - finalBounds.top
                matrix.postTranslate(dx, dy)

                // Draw
                canvas.drawBitmap(bitmap, matrix, null)
                onCarUpdated?.invoke(c.x, c.y, c.direction)
            }
        }
    }


    // Car functions
    fun enableCarPlacement(){
        isSettingStart = true
        isEditMode = false
        isObstacleMode = false
        isDragMode = false

        invalidate()
    }

    fun disableCarPlacement(){
        isSettingStart = false
        onCarPlacedListener?.invoke()
        invalidate()
    }

    var onCarUpdated: ((x: Int, y: Int, direction: Direction) -> Unit)? = null

    var onCarPlacedListener: (() -> Unit)? = null

    fun isPlacingCar(): Boolean = isSettingStart

    fun setCarBitmap(bitmap: Bitmap) {
        carBitmap = bitmap
        invalidate()
    }

    fun getRowCount(): Int = ROW
    fun getColCount(): Int = COL

    private fun drawDirectionIndicator(canvas: Canvas, direction: Direction, left: Float, top: Float, right: Float, bottom: Float) {
        val borderWidth = 4f * density
        directionPaint.strokeWidth = borderWidth

        when (direction) {
            Direction.NORTH -> {
                // Top border
                canvas.drawLine(left, top, right, top, directionPaint)
            }
            Direction.SOUTH -> {
                // Bottom border
                canvas.drawLine(left, bottom, right, bottom, directionPaint)
            }
            Direction.EAST -> {
                // Right border
                canvas.drawLine(right, top, right, bottom, directionPaint)
            }
            Direction.WEST -> {
                // Left border
                canvas.drawLine(left, top, left, bottom, directionPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cellSize == 0f) return false

        val x = event.x
        val y = event.y

        // Convert touch coordinates to grid coordinates
        val gridX = ((x - originX) / cellSize).toInt()
        val gridY = ((y - originY) / cellSize).toInt()

        // Check if touch is within grid bounds
        if (gridX < 0 || gridX >= COL || gridY < 0 || gridY >= ROW) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isObstacleMode) {
                    // Check if there's already an obstacle at this position
                    if (!GridData.hasObstacleAt(gridX, gridY)) {
                        addObstacle(gridX, gridY)
                    }
                    return true
                } else if (isDragMode) {
                    // Find obstacle at this position
                    if (GridData.hasObstacleAt(gridX, gridY)) {
                        val obstacleId = GridData.getObstacleIdAt(gridX, gridY)
                        val direction = GridData.getObstacleDirectionAt(gridX, gridY) ?: Direction.NORTH
                        draggedObstacle = Obstacle(obstacleId, gridX, gridY, direction)
                        isDragging = true
                        dragTouchX = x
                        dragTouchY = y
                        invalidate()
                    }
                    return draggedObstacle != null
                } else if (isEditMode) {
                    // Find obstacle at this position for editing
                    if (GridData.hasObstacleAt(gridX, gridY)) {
                        val obstacleId = GridData.getObstacleIdAt(gridX, gridY)
                        val direction = GridData.getObstacleDirectionAt(gridX, gridY) ?: Direction.NORTH
                        selectedObstacle = Obstacle(obstacleId, gridX, gridY, direction)
                        obstacleListener?.onObstacleEditRequested(selectedObstacle!!)
                    }
                    return selectedObstacle != null
                } else if (isSettingStart) { // flag from MainActivity
                    if (gridX <= 18 && gridY <= 18) { // ensure 2x2 car fits
                        val flippedY = ROW - 1 - gridY
                        val startCar = Car(x = gridX, y = flippedY, direction = Direction.NORTH)
                        GridData.setCar(startCar)
                        setCarBitmap(BitmapFactory.decodeResource(resources, R.drawable.f1_car))

                        // Log the car coordinates
                        Log.d("GridMap", "Car set at coordinates: x=${startCar.x}, y=${startCar.y}")
                    }
                    isSettingStart = false
                    onCarPlacedListener?.invoke()
                    return true
                } else {
                    // Not in placement mode → do nothing on touch
                    return false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragMode && draggedObstacle != null) {
                    // Update floating position; relocate only on drop
                    dragTouchX = x
                    dragTouchY = y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragMode && draggedObstacle != null) {
                    // Recompute drop cell from release position
                    val dropGridX = ((event.x - originX) / cellSize).toInt()
                    val dropGridY = ((event.y - originY) / cellSize).toInt()

                    val originalX = draggedObstacle!!.x
                    val originalY = draggedObstacle!!.y

                    var movedSuccessfully = false
                    // If drop is inside grid, try to move
                    if (dropGridX in 0 until COL && dropGridY in 0 until ROW) {
                        if (!GridData.hasObstacleAt(dropGridX, dropGridY)) {
                            movedSuccessfully = GridData.moveObstacle(
                                originalX,
                                originalY,
                                dropGridX,
                                dropGridY
                            )
                            if (movedSuccessfully) {
                                val updatedObstacle = draggedObstacle!!.copy(x = dropGridX, y = dropGridY)
                                obstacleListener?.onObstacleMoved(updatedObstacle)
                            }
                        }
                    } else {
                        // Dropped outside grid: delete the obstacle
                        GridData.removeObstacle(originalX, originalY)
                    }

                    draggedObstacle = null
                    isDragging = false
                    invalidate()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
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

        // Obstacle paint (black squares)
        obstaclePaint.color = Color.BLACK
        obstaclePaint.style = Paint.Style.FILL

        // Obstacle text (white numbers)
        obstacleTextPaint.color = Color.WHITE
        obstacleTextPaint.textSize = 10f * scaledDensity
        obstacleTextPaint.textAlign = Paint.Align.CENTER
        obstacleTextPaint.isAntiAlias = true

        // Direction indicator (yellow border)
        directionPaint.color = Color.YELLOW
        directionPaint.style = Paint.Style.STROKE
        directionPaint.isAntiAlias = true
    }
}
