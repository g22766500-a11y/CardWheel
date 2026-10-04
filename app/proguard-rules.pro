# JDBC uses service providers and reflection for configuration/authentication.
-keep class org.mariadb.jdbc.** { *; }
-keep class com.example.cardwheel.StrictTlsPlugin { *; }
# Optional desktop JDBC 4.2/4.3, JNDI and Windows native integrations are not used.
-dontwarn java.sql.SQLType
-dontwarn java.sql.ShardingKey
-dontwarn javax.naming.**
-dontwarn com.sun.jna.**
-dontwarn waffle.**
-dontwarn org.slf4j.**
