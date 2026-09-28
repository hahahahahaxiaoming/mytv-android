# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# libVLC 3.x 通过 JNI 按类名和方法名回调 Java。
# libvlc-all 3.7.0 没有随 AAR 提供 consumer ProGuard 规则，Release 混淆时必须保留。
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }

# 保留项目及依赖中的 native 方法名，避免 JNI 查找失败。
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
