# UniFFI 바인딩은 JNA가 리플렉션으로 부른다 → 이름을 바꾸거나 지우면 안 된다.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class app.eodiyeotji.core.rust.** { *; }
-dontwarn java.awt.**

# Pleos SDK는 기능 확인(checkXxxCapabilities) 결과를 Gson TypeToken으로 읽는다.
# R8 full 모드가 익명 TypeToken의 제네릭 정보를 지우면 release에서만 "TypeToken must be created with a type argument"로 실패해
# 차 기능(잔량 등)이 꺼진 것처럼 보였다 → SDK 클래스와 TypeToken 하위 클래스를 그대로 둔다.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class ai.pleos.** { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
