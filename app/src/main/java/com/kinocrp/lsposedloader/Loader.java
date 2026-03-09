package com.kinocrp.lsposedloader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Loader implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String MODULE_PACKAGE_NAME = "com.kinocrp.lsposedloader";
    private static final String LIB_NAME = "hello-world";
    private static String globalModulePath = null;
    private static volatile boolean hasInjected = false;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        globalModulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (hasInjected) return;
        new Thread(() -> {
            Log.i("Kinocrp", "[+] Waiting...");
            Context context = null;
            while (true) {
                try {
                    context = getContextByReflection();
                    if (context != null) {
                        hasInjected = true;
                        Thread.sleep(3000);
                        Log.i("Kinocrp", "[+] Injecting...");
                        extractAndLoad(context);
                        break;
                    }
                    Thread.sleep(10);
                } catch (InterruptedException e) {}
            }
        }).start();
    }

    @SuppressLint("PrivateApi")
    private Context getContextByReflection() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication");
            currentApplicationMethod.setAccessible(true);
            Object app = currentApplicationMethod.invoke(null);
            return (Context) app;
        } catch (Exception e) {
            return null;
        }
    }

    private void extractAndLoad(Context appContext) {
        try {
            String nativeLibDir = appContext.getApplicationInfo().nativeLibraryDir;
            Log.i("Kinocrp", "[+] Native Library Dir: " + nativeLibDir);

            String targetAbi;
            if (nativeLibDir.contains("arm64")) {
                targetAbi = "arm64-v8a";
            } else if (nativeLibDir.contains("arm")) {
                targetAbi = "armeabi-v7a";
            } else if (nativeLibDir.contains("x86_64")) {
                targetAbi = "x86_64";
            } else {
                targetAbi = "x86";
            }

            Log.i("Kinocrp", "[+] ABI: " + targetAbi);

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
                    Log.i("Kinocrp", "[+] Cleaning...");
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
            if (files != null && files.length > 0) return files[0].getAbsolutePath();
        }
        return globalModulePath;
    }
}