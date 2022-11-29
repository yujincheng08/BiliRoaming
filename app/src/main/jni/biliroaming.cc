#include <android/log.h>
#include <dex_helper.h>
#include <fcntl.h>
#include <jni.h>
#include <list>
#include <map>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>

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
jfieldID file_name_field;

struct MemMap {
  MemMap() = default;
  explicit MemMap(std::string file_name) {
    int fd = open(file_name.data(), O_RDONLY | O_CLOEXEC);
    if (fd > 0) {
      struct stat s {};
      fstat(fd, &s);
      auto *addr = mmap(nullptr, s.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
      if (addr != MAP_FAILED) {
        addr_ = static_cast<uint8_t *>(addr);
        len_ = s.st_size;
      }
    }
    close(fd);
  }
  explicit MemMap(size_t size) {
    auto *addr = mmap(nullptr, size, PROT_READ | PROT_WRITE,
                      MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (addr != MAP_FAILED) {
      addr_ = static_cast<uint8_t *>(addr);
      len_ = size;
    }
  }
  ~MemMap() {
    if (ok()) {
      munmap(addr_, len_);
    }
  }

  [[nodiscard]] bool ok() const { return addr_ && len_; }

  [[nodiscard]] auto addr() const { return addr_; }
  [[nodiscard]] auto len() const { return len_; }

  MemMap(MemMap &&other) noexcept : addr_(other.addr_), len_(other.len_) {
    other.addr_ = nullptr;
    other.len_ = 0;
  }
  MemMap &operator=(MemMap &&other) noexcept {
    new (this) MemMap(std::move(other));
    return *this;
  }

  MemMap(const MemMap &) = delete;
  MemMap &operator=(const MemMap &) = delete;

private:
  uint8_t *addr_ = nullptr;
  size_t len_ = 0;
};

struct [[gnu::packed]] ZipLocalFile {
  static ZipLocalFile *from(uint8_t *begin) {
    auto *file = reinterpret_cast<ZipLocalFile *>(begin);
    if (file->signature == 0x4034b50u) {
      return file;
    } else {
      return nullptr;
    }
  }
  ZipLocalFile *next() {
    return from(reinterpret_cast<uint8_t *>(this) + sizeof(ZipLocalFile) +
                file_name_length + extra_length + compress_size);
  }

  MemMap uncompress() {
    if (compress == 0x8) {
      MemMap out(uncompress_size);
      if (!out.ok()) {
        PLOGE("failed to mmap for unocmpression");
        return {};
      }
      z_stream d_stream{
          .next_in = data(),
          .avail_in = compress_size,
          .next_out = out.addr(), /* discard the output */
          .avail_out = static_cast<uInt>(out.len()),
          .zalloc = Z_NULL,
          .zfree = Z_NULL,
          .opaque = Z_NULL,
          .data_type = Z_UNKNOWN,
      }; /* decompression stream */

      for (int err = inflateInit2(&d_stream, -MAX_WBITS); err != Z_STREAM_END;
           err = inflate(&d_stream, Z_NO_FLUSH)) {
        if (err != Z_OK) {
          LOGE("inflate %d", err);
          return {};
        }
      }

      if (int err = inflateEnd(&d_stream); err != Z_OK) {
        LOGE("inflateEnd %d", err);
        return {};
      }

      if (d_stream.total_out != uncompress_size) {
        LOGE("bad inflate: %ld vs %zu\n", d_stream.total_out,
             static_cast<size_t>(uncompress_size));
        return {};
      }
      mprotect(out.addr(), out.len(), PROT_READ);
      return out;
    } else if (compress == 0 && compress_size == uncompress_size) {
      MemMap out(uncompress_size);
      memcpy(out.addr(), data(), uncompress_size);
      mprotect(out.addr(), out.len(), PROT_READ);
      return out;
    }
    LOGW("unsupported compress type");
    return {};
  }

  std::string_view file_name() { return {name, file_name_length}; }

  uint8_t *data() {
    return reinterpret_cast<uint8_t *>(this) + sizeof(ZipLocalFile) +
           file_name_length + extra_length;
  }

  [[maybe_unused]] uint32_t signature;
  [[maybe_unused]] uint16_t version;
  [[maybe_unused]] uint16_t flags;
  [[maybe_unused]] uint16_t compress;
  [[maybe_unused]] uint16_t last_modify_time;
  [[maybe_unused]] uint16_t last_modify_date;
  [[maybe_unused]] uint32_t crc;
  [[maybe_unused]] uint32_t compress_size;
  [[maybe_unused]] uint32_t uncompress_size;
  [[maybe_unused]] uint16_t file_name_length;
  [[maybe_unused]] uint16_t extra_length;
  [[maybe_unused]] char name[0];
};

class ZipFile {
public:
  static std::unique_ptr<ZipFile> Open(const MemMap &map) {
    auto *local_file = ZipLocalFile::from(map.addr());
    if (!local_file)
      return nullptr;
    auto r = std::make_unique<ZipFile>();
    while (local_file) {
      r->entries.emplace(local_file->file_name(), local_file);
      local_file = local_file->next();
    }
    return r;
  }
  ZipLocalFile *Find(std::string_view entry_name) {
    if (auto i = entries.find(entry_name); i != entries.end()) {
      return i->second;
    }
    return nullptr;
  }

private:
  std::map<std::string_view, ZipLocalFile *> entries;
};
static_assert(offsetof(ZipLocalFile, signature) == 0);
static_assert(offsetof(ZipLocalFile, version) == 4);
static_assert(offsetof(ZipLocalFile, flags) == 6);
static_assert(offsetof(ZipLocalFile, compress) == 8);
static_assert(offsetof(ZipLocalFile, last_modify_time) == 10);
static_assert(offsetof(ZipLocalFile, last_modify_date) == 12);
static_assert(offsetof(ZipLocalFile, crc) == 14);
static_assert(offsetof(ZipLocalFile, compress_size) == 18);
static_assert(offsetof(ZipLocalFile, uncompress_size) == 22);
static_assert(offsetof(ZipLocalFile, file_name_length) == 26);
static_assert(offsetof(ZipLocalFile, name) == 30);

using Handler = std::tuple<std::unique_ptr<DexHelper>, std::list<MemMap>>;

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

  virtual ~MyDexFile() = default;
};

extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodUsingString(
    JNIEnv *env, jobject thiz, jstring str, jboolean match_prefix,
    jlong return_type, jshort parameter_count, jstring parameter_shorty,
    jlong declaring_class, jlongArray parameter_types,
    jlongArray contains_parameter_types, jintArray dex_priority,
    jboolean find_first) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return env->NewLongArray(0);
  auto &[helper, _] = *handler;
  if (!str)
    return env->NewLongArray(0);
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
      parameter_types_, contains_parameter_types_, dex_priority_, find_first);

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
  std::list<MemMap> maps;
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
    std::vector<std::tuple<const void *, size_t, const void *, size_t>>
        dex_images;
    if (!dex_files[0]) {
      while (dex_file_length-- > 1) {
        const auto *dex_file = dex_files[dex_file_length];
        LOGD("Got dex file %d", dex_file_length);
        if (!dex_file) {
          LOGD("Skip empty dex file");
          dex_images.clear();
          break;
        }
        if (dex::Reader::IsCompact(dex_file->begin_)) {
          LOGD("skip compact dex");
          dex_images.clear();
          break;
        } else {
          dex_images.emplace_back(dex_file->begin_, dex_file->size_, nullptr,
                                  0);
        }
      }
    }
    if (dex_images.empty()) {
      // contains compact dex, try to load from original file
      auto file_name_obj =
          (jstring)env->GetObjectField(java_dex_file, file_name_field);
      if (!file_name_obj)
        continue;
      auto file_name = env->GetStringUTFChars(file_name_obj, nullptr);
      LOGD("dex filename is %s", file_name);
      auto map = MemMap(file_name);
      if (!map.ok())
        continue;
      auto zip_file = ZipFile::Open(map);
      if (zip_file) {
        for (int idx = 1;; ++idx) {
          auto entry = zip_file->Find(
              "classes" + (idx == 1 ? std::string() : std::to_string(idx)) +
              ".dex");
          if (entry) {
            auto uncompress = entry->uncompress();
            if (uncompress.ok()) {
              LOGD("uncompressed %.*s",
                   static_cast<int>(entry->file_name().size()),
                   entry->file_name().data());
              images.emplace_back(uncompress.addr(), uncompress.len(), nullptr,
                                  0);
              maps.emplace_back(std::move(uncompress));
            } else {
              LOGW("failed to uncompressed %.*s",
                   static_cast<int>(entry->file_name().size()),
                   entry->file_name().data());
            }
          } else {
            break;
          }
        }
      } else {
        images.emplace_back(map.addr(), map.len(), nullptr, 0);
        maps.emplace_back(std::move(map));
      }
      env->ReleaseStringUTFChars(file_name_obj, file_name);
      env->DeleteLocalRef(file_name_obj);
    } else {
      for (auto &image : dex_images) {
        images.emplace_back(std::move(image));
      }
    }
  }
  if (images.empty())
    return 0;
  auto res = reinterpret_cast<jlong>(
      new Handler(std::make_unique<DexHelper>(images), std::move(maps)));
  env->SetLongField(thiz, token_field, res);
  return res;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_findMethodInvoking(
    JNIEnv *env, jobject thiz, jlong method_index, jlong return_type,
    jshort parameter_count, jstring parameter_shorty, jlong declaring_class,
    jlongArray parameter_types, jlongArray contains_parameter_types,
    jintArray dex_priority, jboolean find_first) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return env->NewLongArray(0);
  auto &[helper, _] = *handler;
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
      parameter_types_, contains_parameter_types_, dex_priority_, find_first);

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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return env->NewLongArray(0);
  auto &[helper, _] = *handler;
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
      parameter_types_, contains_parameter_types_, dex_priority_, find_first);

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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return env->NewLongArray(0);
  auto &[helper, _] = *handler;
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
      parameter_types_, contains_parameter_types_, dex_priority_, find_first);

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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return env->NewLongArray(0);
  auto &[helper, _] = *handler;
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
      parameter_types_, contains_parameter_types_, dex_priority_, find_first);

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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return env->NewLongArray(0);
  auto &[helper, _] = *handler;
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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return nullptr;
  auto &[helper, _] = *handler;
  auto out = helper->DecodeMethod(method_index);
  auto cl = env->GetObjectField(thiz, class_loader_field);
  auto clazz = LoadClass(env, cl, out.declaring_class.name);
  if (!clazz)
    return nullptr;
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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return nullptr;
  auto &[helper, _] = *handler;
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
  if (!fid) {
    env->ExceptionClear();
    return nullptr;
  }
  auto res = env->ToReflectedField(clazz, fid, false);
  env->DeleteLocalRef(clazz);
  return res;
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_encodeClassIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jclass clazz) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return -1;
  auto &[helper, _] = *handler;
  return static_cast<jlong>(
      helper->CreateClassIndex(GetClassDescriptor(env, clazz)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_encodeFieldIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jobject field) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return -1;
  auto &[helper, _] = *handler;
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
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return -1;
  auto &[helper, _] = *handler;
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
  auto res = helper->CreateMethodIndex(GetClassDescriptor(env, clazz), name,
                                       params_name);
  env->ReleaseStringUTFChars(java_name, name);
  return static_cast<jlong>(res);
}

extern "C" JNIEXPORT jclass JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_decodeClassIndex(JNIEnv *env,
                                                          jobject thiz,
                                                          jlong class_index) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return nullptr;
  auto &[helper, _] = *handler;
  auto out = helper->DecodeClass(class_index);
  auto cl = env->GetObjectField(thiz, class_loader_field);
  auto res = LoadClass(env, cl, out.name);
  env->DeleteLocalRef(cl);
  return res;
}

extern "C" JNIEXPORT void JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_close(JNIEnv *env, jobject thiz) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  env->SetLongField(thiz, token_field, jlong(0));
  delete handler;
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
  jclass executable = env->FindClass("java/lang/reflect/Executable");
  if (!executable) {
    env->ExceptionClear();
    executable = env->FindClass("java/lang/reflect/AbstractMethod");
  }
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
  file_name_field =
      env->GetFieldID(dex_file, "mFileName", "Ljava/lang/String;");
  return JNI_VERSION_1_4;
}

extern "C" JNIEXPORT void JNICALL
Java_me_iacn_biliroaming_utils_DexHelper_createFullCache(JNIEnv *env,
                                                         jobject thiz) {
  auto *handler =
      reinterpret_cast<Handler *>(env->GetLongField(thiz, token_field));
  if (!handler)
    return;
  auto &[helper, _] = *handler;
  helper->CreateFullCache();
}
