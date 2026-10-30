# XSTZ / InvestmentManager —— R8 规则
#
# 三个必须保留的区域，删任何一条都会在 release 包里炸：
#   1. kotlinx.serialization 生成的序列化器（反射查 $serializer）
#   2. Room 生成的实现类（按类名反射加载 _Impl）
#   3. Hilt 生成的组件与 @InstallIn 模块
#
# 注意 AGP 9 收紧了 -keepattributes 语义：旧的 "*Annotation*" 不再保留 runtime-invisible 注解，
# 需要精确保留 RuntimeVisibleAnnotations。

-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes *Annotation*

# ---------------- kotlinx.serialization ----------------
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.huaying.xstz.**$$serializer { *; }
-keepclassmembers class com.huaying.xstz.** {
    *** Companion;
}
-keepclasseswithmembers class com.huaying.xstz.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留 @Serializable 标注的类自身（R8 会裁掉只被序列化器引用的 data class 的构造器）
-keep @kotlinx.serialization.Serializable class com.huaying.xstz.** { *; }

# ---------------- Room ----------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------------- Hilt / Dagger ----------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# ---------------- OkHttp / Okio ----------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------- password4j（备份口令派生）----------------
-keep class com.password4j.** { *; }
-dontwarn com.password4j.**

# ---------------- 本项目反射入口 ----------------
# 崩溃诊断读 ApplicationExitInfo，不涉及反射；此处仅保留枚举 valueOf 场景
-keepclassmembers enum com.huaying.xstz.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留行号，崩溃栈才有意义（配合 mapping.txt 还原）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
