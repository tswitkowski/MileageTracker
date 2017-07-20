package com.switkows.mileage.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import android.widget.TextView

/**
 * Created by Trevor on 1/1/2015.
 */
class StatisticsView(context: Context, set: AttributeSet) : RelativeLayout(context, set) {

    private lateinit var mLabel: TextView
    private lateinit var mValue: TextView

    public override fun onFinishInflate() {
        super.onFinishInflate()
        mLabel = findViewById<TextView>(android.R.id.text1)
        mValue = findViewById<TextView>(android.R.id.text2)
    }

    fun setLabel(label: String) {
        mLabel.text = label
    }

    fun setValue(str: String) {
        mValue.text = str
    }
}
