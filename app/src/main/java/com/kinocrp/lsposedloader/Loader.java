package com.kinocrp.lsposedloader;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Loader implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String MODULE_PACKAGE_NAME = "com.kinocrp.lsposedloader";
    private static final String LIB_NAME = "hello-world";
    private static String globalModulePath = null;
    private static boolean hasInjected = false;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        globalModulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod("android.content.ContextWrapper", lpparam.classLoader, "attachBaseContext", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (hasInjected) return;

                hasInjected = true;

                Log.i("Kinocrp", "[+] attachBaseContext fired! Grabbing Context...");
                Context appContext = (Context) param.args[0];

                new Thread(() -> {
                    try {
                        Log.i("Kinocrp", "[*] Thread sleeping for 3 seconds...");
                        Thread.sleep(3000);
                        extractAndLoadStealthy(appContext);
                    } catch (Exception e) {
                        Log.e("Kinocrp", "[-] Thread error", e);
                    }
                }).start();
            }
        });
    }

    private void extractAndLoadStealthy(Context appContext) {
        try {
            String targetAbi = android.os.Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a";
            String apkPath = getModuleApkPath(appContext);
            if (apkPath == null) {
                Log.e("Kinocrp", "[-] Could not find module APK on disk");
                return;
            }

            File tempLib = File.createTempFile("sys_core_", ".so", appContext.getCacheDir());

            try (ZipFile zipFile = new ZipFile(apkPath)) {
                String soZipPath = "lib/" + targetAbi + "/lib" + LIB_NAME + ".so";
                ZipEntry entry = zipFile.getEntry(soZipPath);

                if (entry == null) {
                    Log.e("Kinocrp", "[-] Library " + soZipPath + " not found");
                    tempLib.delete();
                    return;
                }

                try (InputStream in = zipFile.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(tempLib)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                System.load(tempLib.getAbsolutePath());
                Log.i("Kinocrp", "[+] Native Library loaded into memory");

            } finally {
                if (tempLib.exists() && tempLib.delete()) {
                    Log.i("Kinocrp", "[+] Temp file cleanup");
                }
            }

        } catch (Exception e) {
            Log.e("Kinocrp", "[-] Injection failed: " + e.getMessage());
        }
    }

    private String getModuleApkPath(Context appContext) {
        File lspatchDir = new File(appContext.getCacheDir(), "lspatch/" + MODULE_PACKAGE_NAME);
        if (lspatchDir.exists() && lspatchDir.isDirectory()) {
            File[] files = lspatchDir.listFiles((dir, name) -> name.endsWith(".apk"));
            if (files != null && files.length > 0) {
                return files[0].getAbsolutePath();
            }
        }

        if (globalModulePath != null) {
            return globalModulePath;
        }

        return null;
    }
}