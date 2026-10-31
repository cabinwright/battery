#!/system/bin/sh
# Runs the battery-log service (service/) under app_process, with dalvik's cache in $TWEAK_DATA.
D=${0%/*}
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
export ANDROID_ROOT=/system ANDROID_ASSETS=/system/app
export BOOTCLASSPATH=/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar:/system/framework/webviewchromium.jar:/system/framework/automotive.jar
export ANDROID_DATA=$TWEAK_DATA/vm
mkdir -p "$ANDROID_DATA/dalvik-cache"
export CLASSPATH=$D/../../lib/battery-log.jar
exec /system/bin/app_process /system/bin app.cabinwright.battery.log.Main "$TWEAK_DATA/samples"
