package com.pcscreencast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * ImageView with pinch-to-zoom and touch-to-mouse support.
 */
class ZoomableStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private var _scale = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastMouseSendTime = 0L
    private val mouseThrottleMs = 30L
    private var lastScrollX = 0f
    private var lastScrollY = 0f
    private var lastScrollSendTime = 0L
    private val scrollThrottleMs = 25L
    private var minScale = 0.5f
    private var maxScale = 4f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            _scale *= detector.scaleFactor
            _scale = _scale.coerceIn(minScale, maxScale)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            sendClick(e.x, e.y, if (tapIsRightClick) 1 else 0)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTapView?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            sendClick(e.x, e.y, if (tapIsRightClick) 0 else 1)
        }
    })

    var onControl: ((type: String, x: Int, y: Int, button: Int) -> Unit)? = null
    var onDoubleTapView: (() -> Unit)? = null
    /** Called with scroll deltas (dx, dy) when user scrolls with two fingers. */
    var onScroll: ((deltaX: Int, deltaY: Int) -> Unit)? = null
    val scale: Float get() = _scale
    var enableZoom = true
    var enableClicks = true
    var tapIsRightClick = false

    init {
        scaleType = ScaleType.MATRIX
        setOnTouchListener { _, event ->
            if (enableZoom) scaleDetector.onTouchEvent(event)
            if (enableClicks) gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    if (event.pointerCount >= 2) {
                        lastScrollX = (event.getX(0) + event.getX(1)) / 2f
                        lastScrollY = (event.getY(0) + event.getY(1)) / 2f
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        lastScrollX = (event.getX(0) + event.getX(1)) / 2f
                        lastScrollY = (event.getY(0) + event.getY(1)) / 2f
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2 && !scaleDetector.isInProgress) {
                        val cx = (event.getX(0) + event.getX(1)) / 2f
                        val cy = (event.getY(0) + event.getY(1)) / 2f
                        val dx = cx - lastScrollX
                        val dy = cy - lastScrollY
                        lastScrollX = cx
                        lastScrollY = cy
                        val now = SystemClock.uptimeMillis()
                        if (now - lastScrollSendTime >= scrollThrottleMs && (dx != 0f || dy != 0f)) {
                            lastScrollSendTime = now
                            val roundDx = dx.toInt().coerceIn(-20, 20)
                            val roundDy = dy.toInt().coerceIn(-20, 20)
                            if (roundDx != 0 || roundDy != 0)
                                onScroll?.invoke(-roundDx, -roundDy)
                        }
                    } else if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y
                        if (_scale > 1f && enableZoom) {
                            addPan(dx, dy)
                        } else if (enableClicks) {
                            sendMouse(event.x, event.y)
                        }
                    }
                }
            }
            true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyMatrix()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        val oldW = drawable?.intrinsicWidth ?: 0
        val oldH = drawable?.intrinsicHeight ?: 0
        super.setImageBitmap(bm)
        val newW = bm?.width ?: 0
        val newH = bm?.height ?: 0
        if (newW != oldW || newH != oldH) {
            if (oldW == 0 && oldH == 0) {
                _scale = 1f
                translateX = 0f
                translateY = 0f
            } else if (newW > oldW || newH > oldH) {
                _scale = 1f
                translateX = 0f
                translateY = 0f
            }
        }
        applyMatrix()
    }

    private fun addPan(dx: Float, dy: Float) {
        translateX += dx
        translateY += dy
        clampTranslation()
        invalidate()
    }

    private fun clampTranslation() {
        val drawable = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        val fitScale = minOf(vw / dw, vh / dh)
        val scaledW = dw * fitScale * _scale
        val scaledH = dh * fitScale * _scale
        val maxTx = (scaledW - vw).coerceAtLeast(0f) / 2
        val maxTy = (scaledH - vh).coerceAtLeast(0f) / 2
        translateX = translateX.coerceIn(-maxTx, maxTx)
        translateY = translateY.coerceIn(-maxTy, maxTy)
    }

    private fun applyMatrix() {
        val drawable = drawable ?: return
        clampTranslation()
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return
        val fitScale = minOf(vw / dw, vh / dh)
        val baseTx = (vw - dw * fitScale) / 2
        val baseTy = (vh - dh * fitScale) / 2
        matrix.reset()
        matrix.postScale(fitScale, fitScale)
        matrix.postTranslate(baseTx, baseTy)
        matrix.postTranslate(-vw / 2, -vh / 2)
        matrix.postScale(_scale, _scale)
        matrix.postTranslate(vw / 2 + translateX, vh / 2 + translateY)
        imageMatrix = matrix
    }

    override fun invalidate() {
        applyMatrix()
        super.invalidate()
    }

    private fun viewToBitmapCoord(vx: Float, vy: Float): Pair<Int, Int>? {
        val drawable = drawable ?: return null
        val inv = Matrix()
        if (!imageMatrix.invert(inv)) return null
        val pts = floatArrayOf(vx, vy)
        inv.mapPoints(pts)
        val dw = drawable.intrinsicWidth
        val dh = drawable.intrinsicHeight
        val bx = pts[0].toInt().coerceIn(0, dw - 1)
        val by = pts[1].toInt().coerceIn(0, dh - 1)
        return Pair(bx, by)
    }

    /** Returns visible region in drawable/bitmap coords: [left, top, width, height] */
    fun getVisibleViewport(): IntArray? {
        val drawable = drawable ?: return null
        val inv = Matrix()
        if (!imageMatrix.invert(inv)) return null
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) return null
        val vw = width.toFloat()
        val vh = height.toFloat()
        val pts = floatArrayOf(0f, 0f, vw, 0f, vw, vh, 0f, vh)
        inv.mapPoints(pts)
        val left = minOf(pts[0], pts[2], pts[4], pts[6])
        val right = maxOf(pts[0], pts[2], pts[4], pts[6])
        val top = minOf(pts[1], pts[3], pts[5], pts[7])
        val bottom = maxOf(pts[1], pts[3], pts[5], pts[7])
        val l = left.toInt().coerceIn(0, dw.toInt())
        val t = top.toInt().coerceIn(0, dh.toInt())
        val r = right.toInt().coerceIn(0, dw.toInt())
        val b = bottom.toInt().coerceIn(0, dh.toInt())
        val w = (r - l).coerceAtLeast(1)
        val h = (b - t).coerceAtLeast(1)
        return intArrayOf(l, t, w, h)
    }

    private fun sendMouse(vx: Float, vy: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastMouseSendTime < mouseThrottleMs) return
        lastMouseSendTime = now
        val coords = viewToBitmapCoord(vx, vy) ?: return
        onControl?.invoke("m", coords.first, coords.second, 0)
    }

    private fun sendClick(vx: Float, vy: Float, button: Int) {
        val coords = viewToBitmapCoord(vx, vy) ?: return
        onControl?.invoke("c", coords.first, coords.second, button)
    }

    fun zoomIn() {
        if (!enableZoom) return
        _scale = (_scale + 0.25f).coerceIn(minScale, maxScale)
        invalidate()
    }

    fun zoomOut() {
        if (!enableZoom) return
        _scale = (_scale - 0.25f).coerceIn(minScale, maxScale)
        invalidate()
    }

    fun resetZoom() {
        _scale = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }
}
