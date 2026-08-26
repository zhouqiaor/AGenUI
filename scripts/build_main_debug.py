#!/usr/bin/env python3
"""Build debug APK from main repo using Python launcher to bypass AV lock."""
import os, sys, subprocess, glob

REPO = "C:/Code/zhouqiaor-AGenUI"
GRADLE_LIB = "C:/Users/georgeslark/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/lib"
AGENT_JAR = GRADLE_LIB + "/agents/gradle-instrumentation-agent-8.11.1.jar"
CLI_JAR = GRADLE_LIB + "/gradle-gradle-cli-main-8.11.1.jar"
NATIVE_DIR = "C:/Users/georgeslark/.gradle/native/1def1411415f61bf3af743bc5b6707747c0891f09f0c88961ee8f79bc544acac/windows-amd64"

# Delete lock files
lock_patterns = [
    os.path.expanduser("~/.gradle/native/**/*.dll.lock"),
    os.path.expanduser("~/.gradle/native/**/*.lock"),
    f"{REPO}/.gradle-home/**/*.lock",
]
deleted = 0
for pattern in lock_patterns:
    for f in glob.glob(pattern, recursive=True):
        try:
            os.remove(f)
            print(f"Deleted lock: {f}")
            deleted += 1
        except Exception:
            pass
print(f"Deleted {deleted} lock files")

target = sys.argv[1] if len(sys.argv) > 1 else "assembleDebug"

cmd = [
    "java", "-Xmx2048m", "-Xms256m",
    f"-javaagent:{AGENT_JAR}",
    f"-Dorg.gradle.native.dir={NATIVE_DIR}",
    f"-Djava.library.path={NATIVE_DIR}",
    "-Dorg.gradle.appname=gradle",
    "-classpath", CLI_JAR,
    "org.gradle.launcher.GradleMain", "--no-daemon", "-p", "playground/android", target
]

env = os.environ.copy()
env["GRADLE_USER_HOME"] = f"{REPO}/.gradle-home"
env["ANDROID_PLAYGROUND_VERSION_NAME"] = "1.0"
env["ANDROID_PLAYGROUND_VERSION_CODE"] = "4"
env["ANDROID_HOME"] = "C:/Programs/Android/Sdk"
env["JAVA_HOME"] = "C:/Programs/Java/jdk-21.0.11"

print(f"Building target: {target}")
print(f"GRADLE_USER_HOME={env['GRADLE_USER_HOME']}")

result = subprocess.run(cmd, env=env, cwd=REPO, capture_output=False, timeout=600)
print(f"\nExit code: {result.returncode}")
sys.exit(result.returncode)
