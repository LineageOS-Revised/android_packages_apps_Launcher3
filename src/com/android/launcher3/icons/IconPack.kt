package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class IconPack(private val pkg: String, private val res: Resources, private val map: Map<String, String>) {

    fun getIcon(packageName: String, className: String?, density: Int): Drawable? {
        val name = className?.let { map["$packageName/$it"] } ?: map[packageName] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        return if (id == 0) null else try {
            res.getDrawableForDensity(id, density, null)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val ACTIONS = arrayOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
        private var cached: IconPack? = null

        @JvmStatic
        fun getPacks(context: Context): List<ResolveInfo> =
            ACTIONS.flatMap { context.packageManager.queryIntentActivities(Intent(it), 0) }
                .distinctBy { it.activityInfo.packageName }

        @JvmStatic
        @Synchronized
        fun get(context: Context, pkg: String): IconPack? {
            if (pkg.isEmpty()) return null
            cached?.takeIf { it.pkg == pkg }?.let { return it }
            return load(context, pkg)?.also { cached = it }
        }

        @JvmStatic
        fun load(context: Context, pkg: String): IconPack? = try {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser = if (id != 0) res.getXml(id) else XmlPullParserFactory.newInstance()
                .newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
            val map = HashMap<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                val cn = parser.getAttributeValue(null, "component")
                    ?.removePrefix("ComponentInfo{")?.removeSuffix("}")
                    ?.let(ComponentName::unflattenFromString) ?: continue
                map.putIfAbsent("${cn.packageName}/${cn.className}", drawable)
                map.putIfAbsent(cn.packageName, drawable)
            }
            IconPack(pkg, res, map)
        } catch (e: Exception) {
            null
        }
    }
}
