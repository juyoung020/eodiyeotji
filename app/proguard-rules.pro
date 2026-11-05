# UniFFI 바인딩은 JNA가 리플렉션으로 부른다 → 이름을 바꾸거나 지우면 안 된다.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class app.eodiyeotji.core.rust.** { *; }
-dontwarn java.awt.**
