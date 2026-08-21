package com.zyplayer.app.util

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.zyplayer.app.R

/**
 * 首次启动免责声明弹窗
 * 用户点击"同意并继续"后才进入应用
 */
object DisclaimerDialog {

    private const val PREFS_NAME = "disclaimer_prefs"
    private const val KEY_ACCEPTED = "disclaimer_accepted"

    /**
     * 检查是否已同意免责声明
     */
    fun isAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACCEPTED, false)
    }

    /**
     * 标记为已同意
     */
    private fun setAccepted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ACCEPTED, true).apply()
    }

    /**
     * 显示免责声明弹窗
     * @param context 上下文
     * @param onAgree 用户同意后的回调（进入应用）
     * @param onExit 用户拒绝后的回调（退出应用）
     */
    fun show(context: Context, onAgree: () -> Unit, onExit: () -> Unit) {
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(context.getString(R.string.disclaimer_title))
            .setMessage(context.getString(R.string.disclaimer_content))
            .setCancelable(false) // 不可点击外部取消
            .setPositiveButton(context.getString(R.string.disclaimer_agree)) { _: DialogInterface, _: Int ->
                setAccepted(context)
                onAgree()
            }
            .setNegativeButton(context.getString(R.string.disclaimer_exit)) { _: DialogInterface, _: Int ->
                onExit()
            }
            .create()

        dialog.show()
    }
}