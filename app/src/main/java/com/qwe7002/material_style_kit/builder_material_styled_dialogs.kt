package com.qwe7002.material_style_kit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.qwe7002.telegram_rc.R

class builder_material_styled_dialogs {
    fun show_about(context: Context, content: String): MaterialStyledDialog {
        val dialog = MaterialStyledDialog.Builder(context)
                .setTitle(R.string.about_title)
                .setDescription(content)
                .setCancelable(false)
                .setHeaderDrawable(R.drawable.feature_graphic)
                .setPositiveText(context.getString(R.string.ok_button))
                .build()
        return dialog
    }

    @Suppress("DEPRECATION")
    fun show_privacy(context: Context): MaterialStyledDialog {
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        return MaterialStyledDialog.Builder(context)
                .setTitle(R.string.privacy_reminder_title)
                .setDescription(R.string.privacy_reminder_information)
                .setCancelable(false)
                .setHeaderDrawable(R.drawable.feature_graphic)
                .setPositiveText(R.string.agree)
                .onPositive { sharedPreferences.edit().putBoolean("privacy_dialog_agree", true).apply() }
                .setNegativeText(R.string.decline)
                .setNeutralText(R.string.visit_page)
                .onNeutral {
                    val uri = Uri.parse("https://get.telegram-sms.com/wiki/" + context.getString(R.string.privacy_policy_url))
                    val privacy_builder = CustomTabsIntent.Builder()
                    privacy_builder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    val customTabsIntent = privacy_builder.build()
                    customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    try {
                        customTabsIntent.launchUrl(context, uri)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                    }
                }
                .build()
    }
}
