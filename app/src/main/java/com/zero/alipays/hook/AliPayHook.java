package com.zero.alipays.hook;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.zero.alipays.config.Const;
import com.zero.alipays.utils.CRequest;
import com.zero.alipays.utils.StringUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

class AliPayHook {
    private static final String TAG = "xposed";
    private boolean mIsLoaded = false;

    private static Context mAlipayContext;

    private ClassLoader mLoader;

    private static AliPayHook _instance;
    private boolean WECHAT_PACKAGE_ISHOOK = false;

    private Gson gson = new Gson();

    public static String userAccount;//登录电话号码
    public static String userName;//登录名字
    public static String userId;//登录id

    public static Object grouoDao; //GroupInfoDaoOp
    public static Object userInfo;

    public static Object userDao;






    public static AliPayHook instance() {
        if (_instance == null) {
            synchronized (AliPayHook.class) {
                if (_instance == null) {
                    _instance = new AliPayHook();

                }
            }
        }
        return _instance;
    }

    public boolean isReadyForUse() {
        return mIsLoaded;
    }


    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loader) throws Throwable {
        if (isReadyForUse()) {
            return;
        }
        mIsLoaded = true;

        final String processName = loader.processName;
        XposedBridge.log("processName " + processName);

        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (Const.ALI_PACKNAME.equals(processName) && !WECHAT_PACKAGE_ISHOOK) {
                    mAlipayContext = (Context) param.args[0];
                    WECHAT_PACKAGE_ISHOOK = true;
                    mLoader = mAlipayContext.getClassLoader();
                    hook();
                }
            }
        });
    }

    public void hook() {
        //过检测
        securityCheckHook();
        ahookDatabaseInsert();
    }

    private void ahookDatabaseInsert() {
        try {
            Class<?> launcherClazz = mLoader.loadClass("com.alipay.mobile.framework.LauncherApplicationAgent");
            final Class<?> finalLauncherClazz = launcherClazz;
            findAndHookMethod(launcherClazz, "init", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Log.d(TAG, "LauncherApplicationAgent init()");
                    Object launcherInstance = param.thisObject;
                    Field applicationContext = finalLauncherClazz.getDeclaredField("mMicroApplicationContext");
                    applicationContext.setAccessible(true);
                    Object microApplicationContext = applicationContext.get(launcherInstance);
                    Class<?> applicationClazz = mLoader.loadClass("com.alipay.mobile.core.impl.MicroApplicationContextImpl");
                    Method getRpcMethod = applicationClazz.getMethod("getExtServiceByInterface", String.class);
                    getRpcMethod.setAccessible(true);
                    Object authServiceInstance = getRpcMethod.invoke(microApplicationContext, "com.alipay.mobile.framework.service.ext.security.AuthService");
                    Method getUserInfoMethod = authServiceInstance.getClass().getDeclaredMethod("getUserInfo");
                    getUserInfoMethod.setAccessible(true);
                      userInfo = getUserInfoMethod.invoke(authServiceInstance);
                    Object logonId = XposedHelpers.findField(userInfo.getClass(), "logonId").get(userInfo);
                    //初始化个人信息和工具
                    if (logonId != null) {
                        userAccount = logonId.toString();
                        userName = XposedHelpers.findField(userInfo.getClass(), "userName").get(userInfo).toString();
                        userId = XposedHelpers.findField(userInfo.getClass(), "userId").get(userInfo).toString();
                    }
                    XposedBridge.log("alipay_userAccount："+userAccount);
                    Class UserIndependentCacheCls = XposedHelpers.findClass("com.alipay.mobile.socialcommonsdk.bizdata.UserIndependentCache", mLoader);
                    Class grouoDaocls = XposedHelpers.findClass("com.alipay.mobile.socialcommonsdk.bizdata.group.data.GroupInfoDaoOp", mLoader);
                    Class AliAccountDaoOp = XposedHelpers.findClass("com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp", mLoader);
                    grouoDao = XposedHelpers.callStaticMethod(UserIndependentCacheCls, "getCacheObj", grouoDaocls);
                    userDao = XposedHelpers.callStaticMethod(UserIndependentCacheCls, "getCacheObj", AliAccountDaoOp);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }



        XposedHelpers.findAndHookMethod("com.alipay.mobile.socialcommonsdk.bizdata.chat.data.GroupChatMsgDaoOp", mLoader, "saveMessages", List.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                List msg = (List) param.args[0];
                if (!msg.isEmpty()) {
                    final Object data = msg.get(0);
                    Object GroupChatMsgDaoOp = param.thisObject;
                    final String f = XposedHelpers.findField(GroupChatMsgDaoOp.getClass(), "f").get(GroupChatMsgDaoOp).toString();
                    XposedHelpers.setObjectField(data, "msgIndex", f);
                    final String templateCode = XposedHelpers.findField(data.getClass(), "templateCode").get(data).toString();
                    String templateData = XposedHelpers.findField(data.getClass(), "templateData").get(data).toString();
                    Log.e("xposed", templateCode + "         " + templateData + (templateCode.contains("8003") && templateData.contains("二维码加入了群聊")));
                    if (templateCode.contains("107")) {
                        final String link = XposedHelpers.findField(data.getClass(), "link").get(data).toString();
                        final String bizRemind = XposedHelpers.findField(data.getClass(), "bizRemind").get(data).toString();
                        Log.e(TAG, "link" + link);
                        Log.e(TAG, "bizRemind" + bizRemind + "状态：");
                        if (bizRemind.equals("[红包]") && templateData.contains("红包")) {

                            String datas = JSON.parseObject(link).getString("link");
                            Log.e("xposedjson", "link:    " + datas);
                            Map<String, String> mapRequest = CRequest.URLRequest(datas);

                            String roomid = StringUtil.getString(JSON.parseObject(link).getString("msgIndex"), "_");
                            Log.e("xposedjson", "roomid:    " + roomid);
                            Class AlipayApplication = findClass("com.alipay.mobile.framework.AlipayApplication", mLoader);
                            Object getInstance = XposedHelpers.callStaticMethod(AlipayApplication, "getInstance");
                            Object getMicroApplicationContext = XposedHelpers.callMethod(getInstance, "getMicroApplicationContext");
                            Object findServiceByInterface = XposedHelpers.callMethod(getMicroApplicationContext, "findServiceByInterface", "com.alipay.mobile.framework.service.common.RpcService");
                            Class GiftCrowdReceiveService = findClass("com.alipay.giftprod.biz.crowd.gw.GiftCrowdReceiveService", mLoader);
                            Object Gift = XposedHelpers.callMethod(findServiceByInterface, "getRpcProxy", GiftCrowdReceiveService);
                            //构造对象
                            //新建一个GiftCrowdReceiveReq
                            Class GiftCrowd = findClass("com.alipay.giftprod.biz.crowd.gw.request.GiftCrowdReceiveReq", mLoader);
                            Object GiftCrowdReceiveReq = XposedHelpers.newInstance(GiftCrowd);
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "crowdNo", mapRequest.get("crowdno"));
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "groupId", roomid);
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "receiverUserType", "2");
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "prevBiz", mapRequest.get("prevbiz"));
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "sign", mapRequest.get("sign"));
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "clientMsgID", mapRequest.get("clientMsgId"));
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "receiverId", XposedHelpers.findField(userInfo.getClass(), "userId").get(userInfo).toString());

                            Map<String, String> extInfo = new HashMap<>();
                            extInfo.put("feedId", "");
                            extInfo.put("canLocalMessage", "Y");
                            extInfo.put("channelName", "");
                            extInfo.put("communityId", "");
                            extInfo.put("receiverUserType", "2");
                            XposedHelpers.setObjectField(GiftCrowdReceiveReq, "extInfo", extInfo);
                            Object gif = XposedHelpers.callMethod(Gift, "receiveCrowd", GiftCrowdReceiveReq);


                            Log.e("xposedjson", gson.toJson(gif));
                        }
                    }

                }
            }
        });
    }

    private void securityCheckHook() {
        try {
            Class<?> securityCheckClazz = XposedHelpers.findClass("com.alipay.mobile.base.security.CI", mLoader);
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", String.class, String.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.getResult();
                    XposedHelpers.setBooleanField(object, "a", false);
                    param.setResult(object);
                    super.afterHookedMethod(param);
                }
            });

            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", Class.class, String.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return (byte) 1;
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", ClassLoader.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return (byte) 1;
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return false;
                }
            });

            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", securityCheckClazz, Activity.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return null;
                }
            });

        } catch (Error | Exception e) {
            XposedBridge.log("Error" + e.toString());
            e.printStackTrace();
        }
    }


}
