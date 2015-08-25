LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_LDLIBS    := -L$(SYSROOT)/usr/lib -llog 
LOCAL_MODULE    := aci_native_util
LOCAL_SRC_FILES := native_helper.cpp

include $(BUILD_SHARED_LIBRARY)
