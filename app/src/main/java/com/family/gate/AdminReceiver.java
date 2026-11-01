package com.family.gate;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 设备管理器：被设为 Device Owner 后（adb 激活，见家长手册），
 * 自动开启"禁止卸载本应用"，系统级防卸载（TCL 自动清理也无法移除）。
 * 解除：管理页登录后调用 /api/owner/clear，或 adb: dpm remove-active-admin（DO 下无效），
 * 正规解除路径是 App 内 clearDeviceOwnerApp。
 */
public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        blockUninstall(context);
    }

    /** 开启禁止卸载（幂等） */
    static void blockUninstall(Context context) {
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setUninstallBlocked(admin, context.getPackageName(), true);
                android.util.Log.i("LearningGate", "防卸载已开启 (device owner)");
            }
        } catch (Exception e) {
            android.util.Log.i("LearningGate", "防卸载开启失败: " + e);
        }
    }

    static boolean isActiveOwner(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }
}
