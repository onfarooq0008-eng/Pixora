# PdfBox-Android (reflection + optional codecs)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**

# Yandex Mobile Ads (consumer rules ship with the SDK)
-dontwarn com.yandex.mobile.ads.**
