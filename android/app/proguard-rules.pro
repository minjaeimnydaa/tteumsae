-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.**

# Keep runtime annotations used by generated serializers and HTTP clients.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
