@echo off
set GRADLE_HOME=C:\Users\georgeslark\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1
set NATIVE_DIR=C:\Users\georgeslark\.gradle\native\1def1411415f61bf3af743bc5b6707747c0891f09f0c88961ee8f79bc544acac\windows-amd64
set CLASSPATH=%GRADLE_HOME%\lib\gradle-gradle-cli-main-8.11.1.jar
set AGENT=%GRADLE_HOME%\lib\agents\gradle-instrumentation-agent-8.11.1.jar
set ANDROID_PLAYGROUND_VERSION_NAME=1.0
set ANDROID_PLAYGROUND_VERSION_CODE=2
set DEFAULT_JVM_OPTS=-Xmx2048m -Xms256m -javaagent:%AGENT%
set GRADLE_OPTS=-Dorg.gradle.native.dir=%NATIVE_DIR% -Djava.library.path=%NATIVE_DIR% -Dgradle.user.home=C:\Users\georgeslark\.gradle

cd /d C:\Code\zhouqiaor-AGenUI\playground\android

java %DEFAULT_JVM_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradle -classpath "%CLASSPATH%" org.gradle.launcher.GradleMain --no-daemon assembleRelease 2>&1
