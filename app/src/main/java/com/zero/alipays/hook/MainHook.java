package com.zero.alipays.hook;

import android.content.pm.ApplicationInfo;

import com.zero.alipays.config.Const;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Administrator on 2018/11/10.
 */

public class MainHook implements IXposedHookLoadPackage {

    private AliPayHook aliPayHook;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (lpparam.appInfo == null || (lpparam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM |
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
            return;
        }
         XposedBridge.log("lpparam.packageName " + lpparam.packageName);
        //注入支付宝
        if(lpparam.packageName.equals(Const.ALI_PACKNAME) ){
            aliPayHook = AliPayHook.instance();

            if(aliPayHook!=null && !aliPayHook.isReadyForUse()){
                aliPayHook.handleLoadPackage(lpparam);
            }
        }



    }



}
