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

# ProGuard rules for NewPipeExtractor & Rhino JS Engine
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
