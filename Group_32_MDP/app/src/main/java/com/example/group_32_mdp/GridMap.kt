package com.example.group_32_mdp

import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
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

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    // Obstacle management
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

    // Arrow/stop icons
    private val arrowUp by lazy { BitmapFactory.decodeResource(resources, R.drawable.arrow_up) }
    private val arrowDown by lazy { BitmapFactory.decodeResource(resources, R.drawable.arrow_down) }
    private val arrowRight by lazy { BitmapFactory.decodeResource(resources, R.drawable.arrow_right) }
    private val arrowLeft by lazy { BitmapFactory.decodeResource(resources, R.drawable.arrow_left) }
    private val stopIcon by lazy { BitmapFactory.decodeResource(resources, R.drawable.stop_icon) }

    // Grid origin coordinates (top-left corner of grid area, not including label bands)
    private var originX: Float = 0f
    private var originY: Float = 0f

    // Callback interfaces
    interface ObstacleInteractionListener {
        fun onObstacleCreated(obstacle: Obstacle)
        fun onObstacleMoved(obstacle: Obstacle)
        fun onObstacleEditRequested(obstacle: Obstacle)
        fun onObstacleRemoved(obstacle: Obstacle)
    }

    private var obstacleListener: ObstacleInteractionListener? = null

    fun setObstacleInteractionListener(listener: ObstacleInteractionListener) {
        this.obstacleListener = listener
    }

    fun setObstacleMode(enabled: Boolean) {
        isObstacleMode = enabled
        if (enabled) {
            isDragMode = false
            isEditMode = false
            isSettingStart = false
        }
        invalidate()
    }

    fun setDragMode(enabled: Boolean) {
        isDragMode = enabled
        if (enabled) {
            isObstacleMode = false
            isEditMode = false
            isSettingStart = false
        }
        invalidate()
    }

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        if (enabled) {
            isObstacleMode = false
            isDragMode = false
            isSettingStart = false
        }
        invalidate()
    }

    fun addObstacle(x: Int, y: Int): Obstacle {
        val obstacle = Obstacle(getNextAvailableId(), x, y)
        GridData.setObstacle(x, y, obstacle.id, obstacle.direction)
        obstacleListener?.onObstacleCreated(obstacle)
        invalidate()
        return obstacle
    }

    private fun getNextAvailableId(): Int {
        val existingIds = mutableSetOf<Int>()
        for (y in 0 until ROW) {
            for (x in 0 until COL) {
                val cell = GridData.getCell(x, y)
                if (cell?.hasObstacle == true) {
                    existingIds.add(cell.obstacleId)
                }
            }
        }
        var id = 1
        while (existingIds.contains(id)) id++
        return id
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

    // -----------------------------
    // Helpers for dp and text sizes
    // -----------------------------
    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun textHeight(paint: Paint): Float {
        val fm = paint.fontMetrics
        return fm.bottom - fm.top
    }

    // -----------------------------
    // Drawing
    // -----------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        initPaints()

        // 1) Reserve measured bands for axis labels
        val yLabelMaxSample = "19" // widest among 0..19
        val yLabelWidth = yTextPaint.measureText(yLabelMaxSample) + dp(6f)    // left band
        val xLabelHeight = textHeight(xTextPaint) + dp(4f)                    // bottom band

        // 2) Available area after padding + label bands
        val leftPad   = paddingLeft.toFloat()
        val topPad    = paddingTop.toFloat()
        val rightPad  = paddingRight.toFloat()
        val bottomPad = paddingBottom.toFloat()

        val availW = width - leftPad - rightPad
        val availH = height - topPad - bottomPad
        val gridMaxW = availW - yLabelWidth
        val gridMaxH = availH - xLabelHeight
        if (gridMaxW <= 0f || gridMaxH <= 0f) return

        // 3) Square cells; expand until one side hits the limit
        cellSize = min(gridMaxW / COL, gridMaxH / ROW)
        val gridW = cellSize * COL
        val gridH = cellSize * ROW

        // 4) Place grid snug against labels (top-left of grid area)
        originX = leftPad + yLabelWidth
        originY = topPad

        // 5) Draw cell backgrounds
        for (x in 0 until COL) {
            for (y in 0 until ROW) {
                val l = originX + x * cellSize
                val t = originY + y * cellSize
                canvas.drawRect(l, t, l + cellSize, t + cellSize, cellPaint)
            }
        }

        // 6) Grid lines (pixel-aligned) - draw first so they're behind everything
        val endX = originX + gridW
        val endY = originY + gridH
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

        // 7) F1 road dotted lines
        drawRoadDottedLines(canvas)

        // 8) Obstacles & car - draw last so they're on top
        drawObstacles(canvas)
        drawCar(canvas)

        // 9) Axis labels - positioned below and to the left of grid
        // X labels (0..19) - positioned below the grid
        val xBaseline = endY + 20f // Simple positioning below grid
        for (x in 0 until COL) {
            val cx = originX + x * cellSize + cellSize / 2f
            canvas.drawText(x.toString(), cx, xBaseline, xTextPaint)
        }

        // Y labels (19 at top to 0 at bottom) - positioned to the left of grid
        val yLabelRight = originX - 10f // Simple positioning to the left
        for (y in 0 until ROW) {
            val label = (ROW - 1 - y).toString()
            val cy = originY + y * cellSize + cellSize / 2f + 5f // Center vertically in cell
            canvas.drawText(label, yLabelRight, cy, yTextPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Let parent decide; provide sensible default size
        val defaultSide = (320 * resources.displayMetrics.density).toInt()
        val w = resolveSize(defaultSide, widthMeasureSpec)
        val h = resolveSize(defaultSide, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    // -----------------------------
    // Obstacles & Car
    // -----------------------------
    private fun drawObstacles(canvas: Canvas) {
        for (y in 0 until ROW) {
            for (x in 0 until COL) {
                val cell = GridData.getCell(x, y)
                if (cell?.hasObstacle == true) {
                    if (isDragging && draggedObstacle != null && draggedObstacle!!.x == x && draggedObstacle!!.y == y) {
                        continue
                    }
                    val drawY = ROW - 1 - y
                    val left = originX + x * cellSize
                    val top = originY + drawY * cellSize
                    val right = left + cellSize
                    val bottom = top + cellSize

                    canvas.drawRect(left, top, right, bottom, obstaclePaint)

                    val textX = left + cellSize / 2f
                    val textY = top + cellSize / 2f + obstacleTextPaint.textSize / 3f

                    val targetId = TargetAssignments.getTargetId(cell.obstacleId)
                    val label = targetId?.let { ObstacleCatalog.idToLabel[it] }

                    when (label) {
                        "UP" -> drawCenteredBitmap(canvas, arrowUp, left, top)
                        "DOWN" -> drawCenteredBitmap(canvas, arrowDown, left, top)
                        "RIGHT" -> drawCenteredBitmap(canvas, arrowRight, left, top)
                        "LEFT" -> drawCenteredBitmap(canvas, arrowLeft, left, top)
                        "STOP" -> drawCenteredBitmap(canvas, stopIcon, left, top)
                        else -> {
                            val textToDraw = label ?: cell.obstacleId.toString()
                            canvas.drawText(textToDraw, textX, textY, obstacleTextPaint)
                        }
                    }

                    drawDirectionIndicator(canvas, cell.direction, left, top, right, bottom)
                }
            }
        }

        // Floating obstacle while dragging
        if (isDragging && draggedObstacle != null) {
            val left = dragTouchX - cellSize / 2f
            val top = dragTouchY - cellSize / 2f
            val right = left + cellSize
            val bottom = top + cellSize

            val draggedPaint = Paint(obstaclePaint).apply { alpha = 160 }
            canvas.drawRect(left, top, right, bottom, draggedPaint)

            val textX = left + cellSize / 2f
            val textY = top + cellSize / 2f + obstacleTextPaint.textSize / 3f
            canvas.drawText(draggedObstacle!!.id.toString(), textX, textY, obstacleTextPaint)
            drawDirectionIndicator(canvas, draggedObstacle!!.direction, left, top, right, bottom)
        }
    }

    private fun drawCenteredBitmap(canvas: Canvas, bitmap: Bitmap, left: Float, top: Float) {
        val destWidth = cellSize * 0.8f
        val destHeight = cellSize * 0.8f
        val scaled = Bitmap.createScaledBitmap(bitmap, destWidth.toInt(), destHeight.toInt(), true)
        val dx = left + (cellSize - destWidth) / 2f
        val dy = top + (cellSize - destHeight) / 2f
        canvas.drawBitmap(scaled, dx, dy, null)
    }

    private fun drawCar(canvas: Canvas) {
        GridData.getCar()?.let { c ->
            carBitmap?.let { bitmap ->
                val drawY = ROW - 1 - c.y
                val left = originX + c.x * cellSize
                val top = originY + drawY * cellSize
                val targetRect = android.graphics.RectF(
                    left,
                    top,
                    left + cellSize * 2f,
                    top + cellSize * 2f
                )

                val matrix = android.graphics.Matrix()
                val angle = when (c.direction) {
                    Direction.NORTH -> 0f
                    Direction.EAST  -> 90f
                    Direction.SOUTH -> 180f
                    Direction.WEST  -> 270f
                }
                val centerX = bitmap.width / 2f
                val centerY = bitmap.height / 2f
                matrix.postRotate(angle, centerX, centerY)

                val rotatedBounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                matrix.mapRect(rotatedBounds)

                val scaleX = targetRect.width() / rotatedBounds.width()
                val scaleY = targetRect.height() / rotatedBounds.height()
                matrix.postScale(scaleX, scaleY, 0f, 0f)

                val finalBounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                matrix.mapRect(finalBounds)

                val dx = targetRect.left - finalBounds.left
                val dy = targetRect.top - finalBounds.top
                matrix.postTranslate(dx, dy)

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

    private fun drawRoadDottedLines(canvas: Canvas) {
        val endX = originX + cellSize * COL
        val endY = originY + cellSize * ROW

        val dottedPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
        }

        for (i in 1 until COL) {
            val xPos = originX + i * cellSize
            canvas.drawLine(xPos, originY, xPos, endY, dottedPaint)
        }
        for (i in 1 until ROW) {
            val yPos = originY + i * cellSize
            canvas.drawLine(originX, yPos, endX, yPos, dottedPaint)
        }
    }

    private fun drawDirectionIndicator(canvas: Canvas, direction: Direction, left: Float, top: Float, right: Float, bottom: Float) {
        val borderWidth = 4f * density
        directionPaint.strokeWidth = borderWidth
        when (direction) {
            Direction.NORTH -> canvas.drawLine(left, top, right, top, directionPaint)
            Direction.SOUTH -> canvas.drawLine(left, bottom, right, bottom, directionPaint)
            Direction.EAST  -> canvas.drawLine(right, top, right, bottom, directionPaint)
            Direction.WEST  -> canvas.drawLine(left, top, left, bottom, directionPaint)
        }
    }

    // Touch handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cellSize == 0f) return false

        val x = event.x
        val y = event.y

        val gridX = ((x - originX) / cellSize).toInt()
        val gridY = ((y - originY) / cellSize).toInt()

        if (isDragMode && draggedObstacle != null) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    dragTouchX = x
                    dragTouchY = y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (gridX in 0 until COL && gridY in 0 until ROW) {
                        val flippedDropY = ROW - 1 - gridY
                        val originalX = draggedObstacle!!.x
                        val originalY = draggedObstacle!!.y

                        if (!GridData.hasObstacleAt(gridX, flippedDropY)) {
                            val movedSuccessfully = GridData.moveObstacle(
                                originalX, originalY, gridX, flippedDropY
                            )
                            if (movedSuccessfully) {
                                val updatedObstacle = draggedObstacle!!.copy(x = gridX, y = flippedDropY)
                                obstacleListener?.onObstacleMoved(updatedObstacle)
                            }
                        }
                    } else {
                        val originalX = draggedObstacle!!.x
                        val originalY = draggedObstacle!!.y
                        GridData.removeObstacle(originalX, originalY)
                        obstacleListener?.onObstacleRemoved(draggedObstacle!!)
                    }

                    draggedObstacle = null
                    isDragging = false
                    invalidate()
                    return true
                }
            }
        }

        if (gridX < 0 || gridX >= COL || gridY < 0 || gridY >= ROW) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isObstacleMode) {
                    val flippedY = ROW - 1 - gridY
                    if (!GridData.hasObstacleAt(gridX, flippedY)) {
                        addObstacle(gridX, flippedY)
                    }
                    return true
                } else if (isDragMode) {
                    val flippedY = ROW - 1 - gridY
                    if (GridData.hasObstacleAt(gridX, flippedY)) {
                        val obstacleId = GridData.getObstacleIdAt(gridX, flippedY)
                        val direction = GridData.getObstacleDirectionAt(gridX, flippedY) ?: Direction.NORTH
                        draggedObstacle = Obstacle(obstacleId, gridX, flippedY, direction)
                        isDragging = true
                        dragTouchX = x
                        dragTouchY = y
                        invalidate()
                    }
                    return draggedObstacle != null
                } else if (isEditMode) {
                    val flippedY = ROW - 1 - gridY
                    if (GridData.hasObstacleAt(gridX, flippedY)) {
                        val obstacleId = GridData.getObstacleIdAt(gridX, flippedY)
                        val direction = GridData.getObstacleDirectionAt(gridX, flippedY) ?: Direction.NORTH
                        selectedObstacle = Obstacle(obstacleId, gridX, flippedY, direction)
                        obstacleListener?.onObstacleEditRequested(selectedObstacle!!)
                    }
                    return selectedObstacle != null
                } else if (isSettingStart) {
                    if (gridX <= 18 && gridY <= 18) {
                        val flippedY = ROW - 1 - gridY
                        val startCar = Car(x = gridX, y = flippedY, direction = Direction.NORTH)
                        GridData.setCar(startCar)
                        setCarBitmap(BitmapFactory.decodeResource(resources, R.drawable.f1_car))
                        Log.d("GridMap", "Car set at coordinates: x=${startCar.x}, y=${startCar.y}")
                    }
                    isSettingStart = false
                    onCarPlacedListener?.invoke()
                    return true
                } else {
                    return false
                }
            }
        }

        return super.onTouchEvent(event)
    }

    private fun initPaints() {
        // F1 Road grid lines - thick teal lines
        gridLinePaint.color = Color.parseColor("#00D2BE") // f1_road_teal
        gridLinePaint.style = Paint.Style.STROKE
        gridLinePaint.strokeWidth = 4f
        gridLinePaint.isAntiAlias = true

        // F1 Road dark background
        cellPaint.color = Color.parseColor("#0B0D0F") // f1_road_dark
        cellPaint.style = Paint.Style.FILL

        // X labels centered with white text for visibility
        xTextPaint.color = Color.WHITE
        xTextPaint.textSize = 12f * scaledDensity
        xTextPaint.textAlign = Paint.Align.CENTER
        xTextPaint.isAntiAlias = true

        // Y labels right-aligned near the grid left edge with white text
        yTextPaint.color = Color.WHITE
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

    // Broadcast to refresh C9 targets
    private val c9Receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "C9_TARGET_UPDATED") {
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LocalBroadcastManager.getInstance(context).registerReceiver(c9Receiver, IntentFilter("C9_TARGET_UPDATED"))
    }

    override fun onDetachedFromWindow() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(c9Receiver)
        super.onDetachedFromWindow()
    }
}
