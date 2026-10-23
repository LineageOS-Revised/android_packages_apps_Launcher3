package com.android.launcher3.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import androidx.preference.Preference
import com.android.launcher3.R
import com.android.launcher3.icons.IconPack
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR

class IconPackPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    private fun updateSummary() {
        val current = getPersistedString("")
        summary = IconPack.getPacks(context).firstOrNull { it.activityInfo.packageName == current }
            ?.loadLabel(context.packageManager) ?: context.getString(R.string.icon_pack_default)
    }

    override fun onClick() {
        val pm = context.packageManager
        val d = context.resources.displayMetrics.density
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val current = getPersistedString("")
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        }
        val dialog = AlertDialog.Builder(context).setTitle(title)
            .setView(ScrollView(context).apply { addView(list) }).create()
        (listOf<ResolveInfo?>(null) + IconPack.getPacks(context)).forEach { ri ->
            val pkg = ri?.activityInfo?.packageName ?: ""
            val previews = LinearLayout(context)
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                addView(RadioButton(context).apply {
                    text = ri?.loadLabel(pm) ?: context.getString(R.string.icon_pack_default)
                    isChecked = pkg == current
                    isClickable = false
                })
                addView(previews)
                setOnClickListener {
                    persistString(pkg)
                    updateSummary()
                    dialog.dismiss()
                }
            })
            UI_HELPER_EXECUTOR.execute {
                val pack = IconPack.load(context, pkg)
                val icons = apps.asSequence().mapNotNull {
                    if (pkg.isEmpty()) it.loadIcon(pm)
                    else pack?.getIcon(it.activityInfo.packageName, it.activityInfo.name, 0)
                }.take(6).toList()
                MAIN_EXECUTOR.execute {
                    icons.forEach {
                        previews.addView(ImageView(context).apply {
                            setImageDrawable(it)
                            layoutParams = LinearLayout.LayoutParams(0, (40 * d).toInt(), 1f)
                        })
                    }
                }
            }
        }
        dialog.show()
    }
}
