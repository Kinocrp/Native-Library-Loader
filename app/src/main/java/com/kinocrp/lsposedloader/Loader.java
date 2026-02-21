package com.kinocrp.lsposedloader;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Loader implements IXposedHookLoadPackage {
    private static final String LIB_NAME = "proxy-loader";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod(android.app.Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Context appContext = (Context) param.thisObject;

                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        extractAndLoadSo(appContext, LIB_NAME);
                    } catch (Exception e) {
                        Log.e("Kinocrp", "[!] Thread error", e);
                    }
                }).start();
            }
        });
    }

    private void extractAndLoadSo(Context appContext, String libName) {
        boolean isLoaded = false;

        try {
            String targetAbi = android.os.Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a";
            Log.i("Kinocrp", "[*] Targeted ABI: " + targetAbi);

            File lspatchDir = new File(appContext.getCacheDir(), "lspatch/com.kinocrp.lsposedloader");
            String apkPath = null;

            if (lspatchDir.exists() && lspatchDir.isDirectory()) {
                File[] files = lspatchDir.listFiles((dir, name) -> name.endsWith(".apk"));
                if (files != null && files.length > 0) {
                    apkPath = files[0].getAbsolutePath();
                }
            }

            if (apkPath == null) {
                apkPath = appContext.getApplicationInfo().sourceDir;
            }

            Log.i("Kinocrp", "[+] Final Source APK Path: " + apkPath);

            File cacheDir = new File(appContext.getCacheDir(), "inject_libs");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File libFile = new File(cacheDir, "lib" + libName + ".so");

            try (ZipFile zipFile = new ZipFile(apkPath)) {
                String soZipPath = "lib/" + targetAbi + "/lib" + libName + ".so";
                ZipEntry entry = zipFile.getEntry(soZipPath);

                if (entry != null) {
                    InputStream in = zipFile.getInputStream(entry);
                    FileOutputStream out = new FileOutputStream(libFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    out.close();
                    in.close();
                    Log.i("Kinocrp", "[+] Extracted " + soZipPath + " to: " + libFile.getAbsolutePath());

                    System.load(libFile.getAbsolutePath());
                    Log.i("Kinocrp", "[+] " + targetAbi + " Library loaded successfully!");
                    isLoaded = true;
                } else {
                    Log.w("Kinocrp", "[!] " + soZipPath + " not found in: " + apkPath);
                }
            }
        } catch (Exception e) {
            Log.e("Kinocrp", "[!] Manual extraction failed: " + e.getMessage());
        }

        if (!isLoaded) {
            try {
                System.loadLibrary(libName);
                Log.i("Kinocrp", "[+] Native Library fully loaded");
            } catch (UnsatisfiedLinkError e) {
                Log.e("Kinocrp", "[!] All load attempts failed", e);
            }
        }
    }
}