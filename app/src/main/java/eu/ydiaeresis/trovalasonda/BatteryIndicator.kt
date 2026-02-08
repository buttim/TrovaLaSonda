package eu.ydiaeresis.trovalasonda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class BatteryIndicator:AppCompatImageView {
    private fun init() {
        paintBlack.color=Color.argb(255,0,0,0)
        paintBlack.textAlign=Paint.Align.CENTER
        paintBlack.typeface=Typeface.create("Arial",Typeface.BOLD)
        paintYellow.color=Color.argb(255,255,255,0)
        paintGray.color=Color.argb(255,160,160,0)
    }

    constructor(context:Context):super(context) {
        init()
    }
    constructor(context:Context,
                attrs:AttributeSet?):super(context,attrs) {
        init()
    }
    constructor(context:Context,
                attrs:AttributeSet?,
                defStyleAttr:Int):super(context,attrs,defStyleAttr) {
        init()
    }

    var chargeLevel:Int?=null
        set(value) {
            field=value
            invalidate()
        }
    private val paintBlack=Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGray=Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintYellow=Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)

        drawBattery(canvas,paintGray)

        val level=if (chargeLevel==null) 1f else chargeLevel!!/100f
        canvas.clipRect(0f,height*.9f*(1-level),width.toFloat(),height.toFloat())
        drawBattery(canvas,paintYellow)
        if (chargeLevel==null) {
            val pt=getTextPoint("?")
            canvas.drawText("?",pt.x,pt.y,paintBlack)
        }
    }

    @Suppress("SameParameterValue")
    private fun getTextPoint(string: String) :PointF {
        val r=Rect()
        paintBlack.getTextBounds(string, 0, string.length, r)
        return PointF(width/2f, height/2f + r.height()/2)
    }

    private fun drawBattery(canvas:Canvas,paint:Paint) {
        canvas.drawRoundRect(width*.35f,0f,width*.65f,height*.9f,10f,10f,paint)
        canvas.drawRoundRect(width*.15f,height*0.1f,width*.85f,height*.9f,20f,20f,paint)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paintBlack.textSize=w.toFloat()
    }
}