package com.flownotes.app.util

import android.app.Activity
import android.content.Context
import com.flownotes.app.R

enum class AppTheme(val id: String, val displayName: String) {
    LIGHT("light", "Light Warm Paper"),
    DARK("dark", "Slate Charcoal Dark"),
    AMOLED("amoled", "AMOLED Pitch Black")
}

object ThemeManager {
    private const val PREFS_NAME = "flownotes_theme_prefs"
    private const val KEY_THEME = "selected_app_theme"

    fun getTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_THEME, AppTheme.LIGHT.id) ?: AppTheme.LIGHT.id
        return AppTheme.values().firstOrNull { it.id == raw } ?: AppTheme.LIGHT
    }

    fun setTheme(activity: Activity, theme: AppTheme) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.id).apply()
        applyTheme(activity)
        activity.recreate()
    }

    fun applyTheme(activity: Activity) {
        when (getTheme(activity)) {
            AppTheme.LIGHT -> activity.setTheme(R.style.Theme_FlowNotes_Light)
            AppTheme.DARK -> activity.setTheme(R.style.Theme_FlowNotes_Dark)
            AppTheme.AMOLED -> activity.setTheme(R.style.Theme_FlowNotes_Amoled)
        }
    }

    fun isDarkOrAmoled(context: Context): Boolean {
        val t = getTheme(context)
        return t == AppTheme.DARK || t == AppTheme.AMOLED
    }

    fun isAmoled(context: Context): Boolean {
        return getTheme(context) == AppTheme.AMOLED
    }
}
