LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := mega_abi
LOCAL_SRC_FILES := mega_abi.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
