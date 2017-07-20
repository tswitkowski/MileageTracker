package com.switkows.mileage

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import android.widget.Checkable
import android.widget.LinearLayout
import android.widget.TextView

class EditRecordListItem : LinearLayout, Checkable {
    var mIDValue: Long = 0  //Database ID for this list item
    var mPosition: Int = 0 //list position
    private var mLabel: TextView? = null
    private var mCheckBox: CheckBox? = null
    private var mAdapter: EditRecordsListAdapter? = null  //pointer to parent (so we can tell them we've been selected)

    constructor(context: Context, att: AttributeSet) : super(context, att)

    constructor(context: Context) : super(context)

    public override fun onFinishInflate() {
        super.onFinishInflate()
        mLabel = findViewById<TextView>(android.R.id.text1)
        mCheckBox = findViewById<CheckBox>(android.R.id.checkbox)

        mCheckBox?.setOnClickListener { _ -> updateSelected() }
    }

    private fun updateSelected() {
        mAdapter?.setSelected(mIDValue, mPosition, mCheckBox!!.isChecked)
        mCheckBox?.toggle()
    }

    internal fun bindAdapter(ad: EditRecordsListAdapter) {
        mAdapter = ad
    }

    fun setText(text: String) {
        mLabel?.text = text
    }

    val text: CharSequence
        get() = mLabel!!.text

    override fun setChecked(checked: Boolean) {
        // Log.d("TJS","ListViewItem.setChecked called with '"+checked+"'");
        mCheckBox?.isChecked = checked
    }

    override fun isChecked(): Boolean {
        return mCheckBox!!.isChecked
    }

    override fun toggle() {
        isChecked = !isChecked
    }
}
