#!/system/bin/sh
# 单次执行 ToolMain 命令（并行副屏专用）：
#   tool_cmd.sh "tree <displayId>"   / "resolve <did> node:<id>" / "set_value_b64 ..."
# 通过 stdin 给 ToolMain daemon 发一条命令，读完后退出。
DEX_PATH="/data/local/tmp/agent_tools.dex"

export ANDROID_ROOT=/system
export ANDROID_DATA=/data
export ANDROID_ART_ROOT=/apex/com.android.art
export ANDROID_I18N_ROOT=/apex/com.android.i18n
export ANDROID_TZDATA_ROOT=/apex/com.android.tzdata

SS_PID=$(/system/bin/pidof system_server 2>/dev/null | awk '{print $1}')
if [ -n "$SS_PID" ] && [ -r "/proc/$SS_PID/environ" ]; then
    if [ -z "$BOOTCLASSPATH" ]; then
        BCP=$(tr '\0' '\n' < "/proc/$SS_PID/environ" 2>/dev/null | grep '^BOOTCLASSPATH=' | head -n 1 | cut -d= -f2-)
        [ -n "$BCP" ] && export BOOTCLASSPATH="$BCP"
    fi
    if [ -z "$DEX2OATBOOTCLASSPATH" ]; then
        DBCP=$(tr '\0' '\n' < "/proc/$SS_PID/environ" 2>/dev/null | grep '^DEX2OATBOOTCLASSPATH=' | head -n 1 | cut -d= -f2-)
        [ -n "$DBCP" ] && export DEX2OATBOOTCLASSPATH="$DBCP"
    fi
fi

export CLASSPATH="$DEX_PATH"
printf '%s\n' "$1" | exec /system/bin/app_process /system/bin com.agent.ToolMain daemon
