LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := onnxruntime
LOCAL_SRC_FILES := ../jniLibs/$(TARGET_ARCH_ABI)/libonnxruntime.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := sherpa_onnx_c_api
LOCAL_SRC_FILES := ../jniLibs/$(TARGET_ARCH_ABI)/libsherpa-onnx-c-api.so
LOCAL_SHARED_LIBRARIES := onnxruntime
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := caption_jni
LOCAL_SRC_FILES := native_caption_jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_SHARED_LIBRARIES := sherpa_onnx_c_api onnxruntime
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
