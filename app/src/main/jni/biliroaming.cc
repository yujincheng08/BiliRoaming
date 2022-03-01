#include <android/log.h>
#include <dex_helper.h>
#include <jni.h>

#define LOG_TAG "BiliRoaming"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__)
#define PLOGE(fmt, args...)                                                    \
  LOGE(fmt " failed with %d: %s", ##args, errno, strerror(errno))

namespace {
jfieldID token_field;
jfieldID class_loader_field;
jmethodID load_class_method;
// jmethodID get_declared_method;
// jmethodID get_declared_field;
jmethodID get_name_method;
jmethodID get_declaring_class_method;
jmethodID get_parameters_method;
jmethodID get_class_name_method;
jfieldID path_list_field;
jfieldID element_field;
jfieldID dex_file_field;
jfieldID cookie_field;

jclass LoadClass(JNIEnv *env, jobject class_loader,
                 std::string_view descriptor) {
  if (descriptor.empty())
    return nullptr;
  if (descriptor[0] != 'L')
    return nullptr;
  std::string name(descriptor.substr(1, descriptor.length() - 2));
  std::replace(name.begin(), name.end(), '/', '.');
  auto java_name = env->NewStringUTF(name.data());
  auto res = (jclass)env->CallObjectMethod(class_loader, load_class_method,
                                           java_name, false);
  return res;
}

std::string GetClassDescriptor(JNIEnv *env, jclass clazz) {
  auto java_name = (jstring)env->CallObjectMethod(clazz, get_class_name_method);
  auto name = env->GetStringUTFChars(java_name, nullptr);
  std::string descriptor = name;
  std::replace(descriptor.begin(), descriptor.end(), '.', '/');
  if (descriptor[0] != '[') {
    if (descriptor == "boolean") {
      descriptor = "Z";
    } else if (descriptor == "byte") {
      descriptor = "B";
    } else if (descriptor == "short") {
      descriptor = "S";
    } else if (descriptor == "char") {
      descriptor = "C";
    } else if (descriptor == "int") {
      descriptor = "I";
    } else if (descriptor == "long") {
      descriptor = "J";
    } else if (descriptor == "float") {
      descriptor = "F";
    } else if (descriptor == "double") {
      descriptor = "D";
    } else {
      descriptor = "L" + descriptor + ";";
    }
  }
  env->ReleaseStringUTFChars(java_name, name);
  return descriptor;
}
} // namespace

struct MyDexFile {
  const void *begin_{};
  size_t size_{};
  const uint8_t *const data_begin_{};
  const size_t data_size_{};

  virtual ~MyDexFile() = default;
};

extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodUsingString(
    JNIEnv *env, jobject thiz, jstring str, jboolean match_prefix,
    jlong return_type, jshort parameter_count, jstring parameter_shorty,
    jlong declaring_class, jlongArray parameter_types,
    jlongArray contains_parameter_types, jintArray dex_priority,
    jboolean find_first) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  if (!str)
    return nullptr;
  auto str_ = env->GetStringUTFChars(str, nullptr);
  auto parameter_shorty_ =
      parameter_shorty ? env->GetStringUTFChars(parameter_shorty, nullptr)
                       : nullptr;
  std::vector<size_t> dex_priority_;
  jint *dex_priority_elements = nullptr;
  if (dex_priority) {
    dex_priority_elements = env->GetIntArrayElements(dex_priority, nullptr);
    dex_priority_.assign(dex_priority_elements,
                         dex_priority_elements +
                             env->GetArrayLength(dex_priority));
  }

  std::vector<size_t> parameter_types_;
  jlong *parameter_types_elements = nullptr;
  if (parameter_types) {
    parameter_types_elements =
        env->GetLongArrayElements(parameter_types, nullptr);
    parameter_types_.assign(parameter_types_elements,
                            parameter_types_elements +
                                env->GetArrayLength(parameter_types));
  }

  std::vector<size_t> contains_parameter_types_;
  jlong *contains_parameter_types_elements = nullptr;
  if (contains_parameter_types) {
    contains_parameter_types_elements =
        env->GetLongArrayElements(contains_parameter_types, nullptr);
    contains_parameter_types_.assign(
        contains_parameter_types_elements,
        contains_parameter_types_elements +
            env->GetArrayLength(contains_parameter_types));
  }

  auto out = helper->FindMethodUsingString(
      str_, match_prefix, return_type, parameter_count,
      parameter_shorty_ ? parameter_shorty_ : "", declaring_class,
      contains_parameter_types_, contains_parameter_types_, dex_priority_,
      find_first);

  env->ReleaseStringUTFChars(str, str_);
  if (parameter_shorty_)
    env->ReleaseStringUTFChars(parameter_shorty, parameter_shorty_);
  if (dex_priority_elements)
    env->ReleaseIntArrayElements(dex_priority, dex_priority_elements,
                                 JNI_ABORT);
  if (parameter_types_elements)
    env->ReleaseLongArrayElements(parameter_types, parameter_types_elements,
                                  JNI_ABORT);
  if (contains_parameter_types_elements)
    env->ReleaseLongArrayElements(contains_parameter_types,
                                  contains_parameter_types_elements, JNI_ABORT);
  auto res = env->NewLongArray(static_cast<int>(out.size()));
  auto res_element = env->GetLongArrayElements(res, nullptr);
  for (size_t i = 0; i < out.size(); ++i) {
    res_element[i] = static_cast<jlong>(out[i]);
  }
  env->ReleaseLongArrayElements(res, res_element, 0);
  return res;
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_load(JNIEnv *env, jobject thiz,
                                              jobject class_loader) {
  if (!class_loader)
    return 0;
  auto path_list = env->GetObjectField(class_loader, path_list_field);
  if (!path_list)
    return 0;
  auto elements = (jobjectArray)env->GetObjectField(path_list, element_field);
  if (!elements)
    return 0;
  std::vector<std::tuple<const void *, size_t, const void *, size_t>> images;
  for (auto i = 0, len = env->GetArrayLength(elements); i < len; ++i) {
    auto element = env->GetObjectArrayElement(elements, i);
    if (!element)
      continue;
    auto java_dex_file = env->GetObjectField(element, dex_file_field);
    if (!java_dex_file)
      continue;
    auto cookie = (jlongArray)env->GetObjectField(java_dex_file, cookie_field);
    if (!cookie)
      continue;
    auto dex_file_length = env->GetArrayLength(cookie);
    const auto *dex_files = reinterpret_cast<const MyDexFile **>(
        env->GetLongArrayElements(cookie, nullptr));
    while (dex_file_length-- > 1) {
      const auto *dex_file = dex_files[dex_file_length];
      LOGD("Got dex file %d", dex_file_length);
      if (dex::Reader::IsCompact(dex_file->begin_)) {
        LOGD("compact dex");
        images.emplace_back(dex_file->begin_, dex_file->size_,
                            dex_file->data_begin_, dex_file->data_size_);
      } else {
        images.emplace_back(dex_file->begin_, dex_file->size_, nullptr, 0);
      }
    }
  }
  if (images.empty())
    return 0;
  auto res = reinterpret_cast<jlong>(new DexHelper(images));
  env->SetLongField(thiz, token_field, res);
  return res;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodInvoking(
    JNIEnv *env, jobject thiz, jlong method_index, jlong return_type,
    jshort parameter_count, jstring parameter_shorty, jlong declaring_class,
    jlongArray parameter_types, jlongArray contains_parameter_types,
    jintArray dex_priority, jboolean find_first) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto parameter_shorty_ =
      parameter_shorty ? env->GetStringUTFChars(parameter_shorty, nullptr)
                       : nullptr;
  std::vector<size_t> dex_priority_;
  jint *dex_priority_elements = nullptr;
  if (dex_priority) {
    dex_priority_elements = env->GetIntArrayElements(dex_priority, nullptr);
    dex_priority_.assign(dex_priority_elements,
                         dex_priority_elements +
                             env->GetArrayLength(dex_priority));
  }

  std::vector<size_t> parameter_types_;
  jlong *parameter_types_elements = nullptr;
  if (parameter_types) {
    parameter_types_elements =
        env->GetLongArrayElements(parameter_types, nullptr);
    parameter_types_.assign(parameter_types_elements,
                            parameter_types_elements +
                                env->GetArrayLength(parameter_types));
  }

  std::vector<size_t> contains_parameter_types_;
  jlong *contains_parameter_types_elements = nullptr;
  if (contains_parameter_types) {
    contains_parameter_types_elements =
        env->GetLongArrayElements(contains_parameter_types, nullptr);
    contains_parameter_types_.assign(
        contains_parameter_types_elements,
        contains_parameter_types_elements +
            env->GetArrayLength(contains_parameter_types));
  }

  auto out = helper->FindMethodInvoking(
      method_index, return_type, parameter_count,
      parameter_shorty_ ? parameter_shorty_ : "", declaring_class,
      contains_parameter_types_, contains_parameter_types_, dex_priority_,
      find_first);

  if (parameter_shorty_)
    env->ReleaseStringUTFChars(parameter_shorty, parameter_shorty_);
  if (dex_priority_elements)
    env->ReleaseIntArrayElements(dex_priority, dex_priority_elements,
                                 JNI_ABORT);
  if (parameter_types_elements)
    env->ReleaseLongArrayElements(parameter_types, parameter_types_elements,
                                  JNI_ABORT);
  if (contains_parameter_types_elements)
    env->ReleaseLongArrayElements(contains_parameter_types,
                                  contains_parameter_types_elements, JNI_ABORT);
  auto res = env->NewLongArray(static_cast<int>(out.size()));
  auto res_element = env->GetLongArrayElements(res, nullptr);
  for (size_t i = 0; i < out.size(); ++i) {
    res_element[i] = static_cast<jlong>(out[i]);
  }
  env->ReleaseLongArrayElements(res, res_element, 0);
  return res;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodInvoked(
    JNIEnv *env, jobject thiz, jlong method_index, jlong return_type,
    jshort parameter_count, jstring parameter_shorty, jlong declaring_class,
    jlongArray parameter_types, jlongArray contains_parameter_types,
    jintArray dex_priority, jboolean find_first) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto parameter_shorty_ =
      parameter_shorty ? env->GetStringUTFChars(parameter_shorty, nullptr)
                       : nullptr;
  std::vector<size_t> dex_priority_;
  jint *dex_priority_elements = nullptr;
  if (dex_priority) {
    dex_priority_elements = env->GetIntArrayElements(dex_priority, nullptr);
    dex_priority_.assign(dex_priority_elements,
                         dex_priority_elements +
                             env->GetArrayLength(dex_priority));
  }

  std::vector<size_t> parameter_types_;
  jlong *parameter_types_elements = nullptr;
  if (parameter_types) {
    parameter_types_elements =
        env->GetLongArrayElements(parameter_types, nullptr);
    parameter_types_.assign(parameter_types_elements,
                            parameter_types_elements +
                                env->GetArrayLength(parameter_types));
  }

  std::vector<size_t> contains_parameter_types_;
  jlong *contains_parameter_types_elements = nullptr;
  if (contains_parameter_types) {
    contains_parameter_types_elements =
        env->GetLongArrayElements(contains_parameter_types, nullptr);
    contains_parameter_types_.assign(
        contains_parameter_types_elements,
        contains_parameter_types_elements +
            env->GetArrayLength(contains_parameter_types));
  }

  auto out = helper->FindMethodInvoked(
      method_index, return_type, parameter_count,
      parameter_shorty_ ? parameter_shorty_ : "", declaring_class,
      contains_parameter_types_, contains_parameter_types_, dex_priority_,
      find_first);

  if (parameter_shorty_)
    env->ReleaseStringUTFChars(parameter_shorty, parameter_shorty_);
  if (dex_priority_elements)
    env->ReleaseIntArrayElements(dex_priority, dex_priority_elements,
                                 JNI_ABORT);
  if (parameter_types_elements)
    env->ReleaseLongArrayElements(parameter_types, parameter_types_elements,
                                  JNI_ABORT);
  if (contains_parameter_types_elements)
    env->ReleaseLongArrayElements(contains_parameter_types,
                                  contains_parameter_types_elements, JNI_ABORT);
  auto res = env->NewLongArray(static_cast<int>(out.size()));
  auto res_element = env->GetLongArrayElements(res, nullptr);
  for (size_t i = 0; i < out.size(); ++i) {
    res_element[i] = static_cast<jlong>(out[i]);
  }
  env->ReleaseLongArrayElements(res, res_element, 0);
  return res;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodSettingField(
    JNIEnv *env, jobject thiz, jlong field_index, jlong return_type,
    jshort parameter_count, jstring parameter_shorty, jlong declaring_class,
    jlongArray parameter_types, jlongArray contains_parameter_types,
    jintArray dex_priority, jboolean find_first) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto parameter_shorty_ =
      parameter_shorty ? env->GetStringUTFChars(parameter_shorty, nullptr)
                       : nullptr;
  std::vector<size_t> dex_priority_;
  jint *dex_priority_elements = nullptr;
  if (dex_priority) {
    dex_priority_elements = env->GetIntArrayElements(dex_priority, nullptr);
    dex_priority_.assign(dex_priority_elements,
                         dex_priority_elements +
                             env->GetArrayLength(dex_priority));
  }

  std::vector<size_t> parameter_types_;
  jlong *parameter_types_elements = nullptr;
  if (parameter_types) {
    parameter_types_elements =
        env->GetLongArrayElements(parameter_types, nullptr);
    parameter_types_.assign(parameter_types_elements,
                            parameter_types_elements +
                                env->GetArrayLength(parameter_types));
  }

  std::vector<size_t> contains_parameter_types_;
  jlong *contains_parameter_types_elements = nullptr;
  if (contains_parameter_types) {
    contains_parameter_types_elements =
        env->GetLongArrayElements(contains_parameter_types, nullptr);
    contains_parameter_types_.assign(
        contains_parameter_types_elements,
        contains_parameter_types_elements +
            env->GetArrayLength(contains_parameter_types));
  }

  auto out = helper->FindMethodSettingField(
      field_index, return_type, parameter_count,
      parameter_shorty_ ? parameter_shorty_ : "", declaring_class,
      contains_parameter_types_, contains_parameter_types_, dex_priority_,
      find_first);

  if (parameter_shorty_)
    env->ReleaseStringUTFChars(parameter_shorty, parameter_shorty_);
  if (dex_priority_elements)
    env->ReleaseIntArrayElements(dex_priority, dex_priority_elements,
                                 JNI_ABORT);
  if (parameter_types_elements)
    env->ReleaseLongArrayElements(parameter_types, parameter_types_elements,
                                  JNI_ABORT);
  if (contains_parameter_types_elements)
    env->ReleaseLongArrayElements(contains_parameter_types,
                                  contains_parameter_types_elements, JNI_ABORT);
  auto res = env->NewLongArray(static_cast<int>(out.size()));
  auto res_element = env->GetLongArrayElements(res, nullptr);
  for (size_t i = 0; i < out.size(); ++i) {
    res_element[i] = static_cast<jlong>(out[i]);
  }
  env->ReleaseLongArrayElements(res, res_element, 0);
  return res;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodGettingField(
    JNIEnv *env, jobject thiz, jlong field_index, jlong return_type,
    jshort parameter_count, jstring parameter_shorty, jlong declaring_class,
    jlongArray parameter_types, jlongArray contains_parameter_types,
    jintArray dex_priority, jboolean find_first) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto parameter_shorty_ =
      parameter_shorty ? env->GetStringUTFChars(parameter_shorty, nullptr)
                       : nullptr;
  std::vector<size_t> dex_priority_;
  jint *dex_priority_elements = nullptr;
  if (dex_priority) {
    dex_priority_elements = env->GetIntArrayElements(dex_priority, nullptr);
    dex_priority_.assign(dex_priority_elements,
                         dex_priority_elements +
                             env->GetArrayLength(dex_priority));
  }

  std::vector<size_t> parameter_types_;
  jlong *parameter_types_elements = nullptr;
  if (parameter_types) {
    parameter_types_elements =
        env->GetLongArrayElements(parameter_types, nullptr);
    parameter_types_.assign(parameter_types_elements,
                            parameter_types_elements +
                                env->GetArrayLength(parameter_types));
  }

  std::vector<size_t> contains_parameter_types_;
  jlong *contains_parameter_types_elements = nullptr;
  if (contains_parameter_types) {
    contains_parameter_types_elements =
        env->GetLongArrayElements(contains_parameter_types, nullptr);
    contains_parameter_types_.assign(
        contains_parameter_types_elements,
        contains_parameter_types_elements +
            env->GetArrayLength(contains_parameter_types));
  }

  auto out = helper->FindMethodGettingField(
      field_index, return_type, parameter_count,
      parameter_shorty_ ? parameter_shorty_ : "", declaring_class,
      contains_parameter_types_, contains_parameter_types_, dex_priority_,
      find_first);

  if (parameter_shorty_)
    env->ReleaseStringUTFChars(parameter_shorty, parameter_shorty_);
  if (dex_priority_elements)
    env->ReleaseIntArrayElements(dex_priority, dex_priority_elements,
                                 JNI_ABORT);
  if (parameter_types_elements)
    env->ReleaseLongArrayElements(parameter_types, parameter_types_elements,
                                  JNI_ABORT);
  if (contains_parameter_types_elements)
    env->ReleaseLongArrayElements(contains_parameter_types,
                                  contains_parameter_types_elements, JNI_ABORT);
  auto res = env->NewLongArray(static_cast<int>(out.size()));
  auto res_element = env->GetLongArrayElements(res, nullptr);
  for (size_t i = 0; i < out.size(); ++i) {
    res_element[i] = static_cast<jlong>(out[i]);
  }
  env->ReleaseLongArrayElements(res, res_element, 0);
  return res;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findField(JNIEnv *env, jobject thiz,
                                                   jlong type,
                                                   jintArray dex_priority,
                                                   jboolean find_first) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  std::vector<size_t> dex_priority_;
  jint *dex_priority_elements = nullptr;
  if (dex_priority) {
    dex_priority_elements = env->GetIntArrayElements(dex_priority, nullptr);
    dex_priority_.assign(dex_priority_elements,
                         dex_priority_elements +
                             env->GetArrayLength(dex_priority));
  }

  auto out = helper->FindField(type, dex_priority_, find_first);

  if (dex_priority_elements)
    env->ReleaseIntArrayElements(dex_priority, dex_priority_elements,
                                 JNI_ABORT);

  auto res = env->NewLongArray(static_cast<int>(out.size()));
  auto res_element = env->GetLongArrayElements(res, nullptr);
  for (size_t i = 0; i < out.size(); ++i) {
    res_element[i] = static_cast<jlong>(out[i]);
  }
  env->ReleaseLongArrayElements(res, res_element, 0);
  return res;
}

extern "C" JNIEXPORT jobject JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_decodeMethodIndex(JNIEnv *env,
                                                           jobject thiz,
                                                           jlong method_index) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto out = helper->DecodeMethod(method_index);
  auto cl = env->GetObjectField(thiz, class_loader_field);
  auto clazz = LoadClass(env, cl, out.declaring_class.name);
  env->DeleteLocalRef(cl);
  std::string sig;
  sig.reserve(4096);
  sig += "(";
  for (const auto &param : out.parameters) {
    sig += param.name;
  }
  sig += ")";
  sig += out.return_type.name;
  auto *method = env->GetMethodID(clazz, out.name.data(), sig.data());
  if (!method) {
    env->ExceptionClear();
    method = env->GetStaticMethodID(clazz, out.name.data(), sig.data());
  }
  if (!method) {
    env->ExceptionClear();
    return nullptr;
  }
  return env->ToReflectedMethod(clazz, method, false);
}

extern "C" JNIEXPORT jobject JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_decodeFieldIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jlong field_index) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto out = helper->DecodeField(field_index);
  auto cl = env->GetObjectField(thiz, class_loader_field);
  auto clazz = LoadClass(env, cl, out.declaring_class.name);
  if (!clazz)
    return nullptr;
  env->DeleteLocalRef(cl);
  auto fid = env->GetFieldID(clazz, out.name.data(), out.type.name.data());
  if (!fid) {
    env->ExceptionClear();
    fid = env->GetStaticFieldID(clazz, out.name.data(), out.type.name.data());
  }
//  if (!fid) {
  //env->ExceptionClear();
//    return nullptr; }
  auto res = env->ToReflectedField(clazz, fid, false);
  env->DeleteLocalRef(clazz);
  return res;
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_encodeClassIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jclass clazz) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return -1;
  return static_cast<jlong>(
      helper->CreateClassIndex(GetClassDescriptor(env, clazz)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_encodeFieldIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jobject field) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return -1;
  auto java_name = (jstring)env->CallObjectMethod(field, get_name_method);
  auto clazz = (jclass)env->CallObjectMethod(field, get_declaring_class_method);
  auto name = env->GetStringUTFChars(java_name, nullptr);
  auto res = helper->CreateFieldIndex(GetClassDescriptor(env, clazz), name);
  env->ReleaseStringUTFChars(java_name, name);
  return static_cast<jlong>(res);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_encodeMethodIndex(JNIEnv *env,
                                                           jobject thiz,
                                                           jobject method) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return -1;
  auto java_name = (jstring)env->CallObjectMethod(method, get_name_method);
  auto clazz =
      (jclass)env->CallObjectMethod(method, get_declaring_class_method);
  auto name = env->GetStringUTFChars(java_name, nullptr);
  auto params =
      (jobjectArray)env->CallObjectMethod(method, get_parameters_method);
  auto param_len = env->GetArrayLength(params);
  std::vector<std::string> param_descriptors;
  param_descriptors.reserve(param_len);
  for (int i = 0; i < param_len; ++i) {
    auto param = (jclass)env->GetObjectArrayElement(params, i);
    param_descriptors.emplace_back(GetClassDescriptor(env, param));
    env->DeleteLocalRef(param);
  }
  std::vector<std::string_view> params_name;
  params_name.reserve(param_descriptors.size());
  for (const auto &descriptor : param_descriptors) {
    params_name.emplace_back(descriptor);
  }
  auto res = helper->CreateMethodIndex(name, GetClassDescriptor(env, clazz),
                                       params_name);
  env->ReleaseStringUTFChars(java_name, name);
  return static_cast<jlong>(res);
}

extern "C" JNIEXPORT jclass JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_decodeClassIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jlong class_index) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return nullptr;
  auto out = helper->DecodeClass(class_index);
  auto cl = env->GetObjectField(thiz, class_loader_field);
  auto res = LoadClass(env, cl, out.name);
  env->DeleteLocalRef(cl);
  return res;
}

extern "C" JNIEXPORT void JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_close(JNIEnv *env, jobject thiz) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  env->SetLongField(thiz, token_field, jlong(0));
  delete helper;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
  JNIEnv *env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_4) != JNI_OK) {
    return -1;
  }
  jclass helper = env->FindClass("me/iacn/biliroaming/utils/DexHelper");
  token_field = env->GetFieldID(helper, "token", "J");
  class_loader_field =
      env->GetFieldID(helper, "classLoader", "Ljava/lang/ClassLoader;");
  auto class_loader = env->FindClass("java/lang/ClassLoader");
  load_class_method = env->GetMethodID(
      class_loader, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;");
  auto member = env->FindClass("java/lang/reflect/Member");
  get_declaring_class_method =
      env->GetMethodID(member, "getDeclaringClass", "()Ljava/lang/Class;");
  get_name_method = env->GetMethodID(member, "getName", "()Ljava/lang/String;");
  auto executable = env->FindClass("java/lang/reflect/Executable");
  get_parameters_method =
      env->GetMethodID(executable, "getParameterTypes", "()[Ljava/lang/Class;");
  auto clazz = env->FindClass("java/lang/Class");
  get_class_name_method =
      env->GetMethodID(clazz, "getName", "()Ljava/lang/String;");
  auto dex_class_loader = env->FindClass("dalvik/system/BaseDexClassLoader");
  path_list_field = env->GetFieldID(dex_class_loader, "pathList",
                                    "Ldalvik/system/DexPathList;");
  auto dex_path_list = env->FindClass("dalvik/system/DexPathList");
  element_field = env->GetFieldID(dex_path_list, "dexElements",
                                  "[Ldalvik/system/DexPathList$Element;");
  auto element = env->FindClass("dalvik/system/DexPathList$Element");
  dex_file_field =
      env->GetFieldID(element, "dexFile", "Ldalvik/system/DexFile;");
  auto dex_file = env->FindClass("dalvik/system/DexFile");
  cookie_field = env->GetFieldID(dex_file, "mCookie", "Ljava/lang/Object;");
  return JNI_VERSION_1_4;
}

extern "C" JNIEXPORT void JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_createFullCache(JNIEnv *env,
                                                         jobject thiz) {
  auto *helper =
      reinterpret_cast<DexHelper *>(env->GetLongField(thiz, token_field));
  if (!helper)
    return;
  helper->CreateFullCache();
}
