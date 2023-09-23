# Android use Shared library which the Rust generated.

**Android NDK** 可以使用一些第三方的动态库, 如何用 **Rust** 写个东西生成动态库, 给 **Cpp** 这边调用, 这边记录一下过程.

## 配置 Rust 工程

首先写个 **Rust** 工程, 搞出个动态库出来, 先是创建个项目, 这里取名叫 **ffi-example**

```bash
cargo new ffi-example --lib
```
打开 Cargo.toml 文件, 里面的内容长这样

```toml
[package]
name = "ffi-example"
version = "0.1.0"
edition = "2021"

# See more keys and their definitions at https://doc.rust-lang.org/cargo/reference/manifest.html
[lib]
name = "ffi_example"
crate-type = ["staticlib", "cdylib"]

[dependencies]
md5 = "0.7"
```

我们的初衷是为了把 **Rust** 生成的动态库给 **Android** 端使用, 这里就不添加 `jni` 相关的 **crate** 了, 如果要写很多 native 的代码, 建议补上这个 **crate**. 这个工程主要是使用到了一个 `md5` 的 **crate**, 顺便把后续要生成的 **crate** 类型标注成 staticlib 跟 cdylib.

然后跳到工程中的 lib.rs 文件, 把里面的内容改成下面这些

```rust
use md5::compute;
use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_uchar};

#[no_mangle]
extern "C" fn ll_md5(buf: *const c_char) -> *const c_uchar {
  let buf = unsafe { CStr::from_ptr(buf) }.to_str().unwrap().as_bytes();
  let digest = format!("{:x}", compute(buf));
  CString::new(digest).unwrap().into_raw() as *const c_uchar
}
```

代码可以加点自己的 lint, 可以补充个 rustfmt.toml 文件, 譬如我这里用得是两个空格的代码风格

```toml
tab_spaces = 2
```

现在把我们的代码构建成动态库, 可以把对应 **x86** 的 **target** 安装上, 安装对应的 **target**, 需要用 **rustup** 安装, 可以先搜索一下有哪些 **target**

```bash
rustup target list
```

如果你用得是水果 **M1** 芯片的设备, 可以直接使用 ARM64 的 Android 仿真器, 下面这条命令就可以兼顾 Android 真机跟 M1 上的 Android 仿真器 (只要你 Android Studio 设置的仿真器是 ARM64 的)

```bash
rustup target add aarch64-linux-android
```
假设你已经装好了必要的 **target**, 可以执行下面的命令打包

```bash
cargo build --target aarch64-linux-android --release
```
然后我们看到工程的 target 文件夹下生成了一个 **aarch64-linux-android** 文件夹, 里面的 **release** 文件夹下就有我们想要的 **libffi_example.so** 文件

### 如果编译出错
其实还有一个事情没讲, 那就是 Rust 编译 Android 可用的动态库, 需要配置 NDK standalone.
先把 ndk 装好, 直接在 Android Studio SDK Tools 的 NDK (Side by side) 选一个版本安装.

然后执行下面的命令, 具体目录根据自己的情况而定

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/21.4.7075529
cd ~/Library/Android/sdk/ndk
python3 $ANDROID_NDK_HOME/build/tools/make_standalone_toolchain.py --api 28 --arch arm64 --install-dir ./arm64
```

上面只处理 arm64 的情况, 具体 ABI 根据自己的需要而定, 然后设置一下 .cargo/config 里面的内容

```
[target.aarch64-linux-android]
ar = "/Users/your name/Library/Android/sdk/ndk/arm64/bin/aarch64-linux-android-ar"
linker = "/Users/your name/Library/Android/sdk/ndk/arm64/bin/aarch64-linux-android-clang"

[target.armv7-linux-androideabi]
ar = "/Users/your name/Library/Android/sdk/ndk/arm/bin/arm-linux-androideabi-ar"
linker = "/Users/your name/Library/Android/sdk/ndk/arm/bin/arm-linux-androideabi-clang"

[target.i686-linux-android]
ar = "/Users/your name/Library/Android/sdk/ndk/x86/bin/i686-linux-android-ar"
linker = "/Users/your name/Library/Android/sdk/ndk/x86/bin/i686-linux-android-clang"
```
其实就是根据你指定的 target 使用用对应平台的链接器, 这里建议使用 NDK 的版本是 21, 更高版本的我还没测试过能不能编译通过.

## 配置 Android 工程

现在用 Android Studio 来创建个 Android 的项目, 模板选择 Cpp 的那个, 语言不论 Kotlin 还是 Java 都可以, Minimum SDK 随便选一个, 我这里选得是 API 26 以上的.

接着要来改改配置, 找到项目中的 CMakeLists.txt 文件, 在 **find_library** 上面添加一些内容, 这里的 `CMAKE_ANDROID_ARCH_ABI` 对根据环境自动指定对应的文件夹(**target_link_libraries** 也要加入相应的动态库名字)

```cmake
add_library(ffi_example SHARED IMPORTED)
set_target_properties(ffi_example PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/lib/${CMAKE_ANDROID_ARCH_ABI}/libffi_example.so)

# ...

target_link_libraries( # Specifies the target library.
        ffidemo
        # 你导入的动态库
        ffi_example

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
```

然后把之前生成的动态库拷贝到 **Android** 项目中来, 直接放到 `src/main/cpp/lib/arm64-v8a` 目录下(如果你有其他的 **ABI** 的动态库, 你也可以加上对应的文件夹, 放入相应的动态库), 网上很多文章说要放到 **libs** 或者 **jniLibs** 之类的文件夹, 现在新版本不需要这样做了, 我们以官方的文档为准. 此外, `build.gradle (:app)` 可以把对应的 **ndkVersion** 加上

```gradle
android {
    compileSdk 32

    defaultConfig {
        applicationId "wiki.mdzz.ffidemo"
        minSdk 26
        targetSdk 32
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags ''
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = '11'
    }
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }
    buildFeatures {
        viewBinding true
    }
    ndkVersion '21.4.7075529'
}
```
然后我们执行一下, 就会发现, 你的 **Logcat** 告诉你说

```
java.lang.UnsatisfiedLinkError: dlopen failed: library "~/FFIDemo/app/src/main/cpp/lib/arm64-v8a/libffi_example.so" not found
```

我们把 app-debug.apk 的文件拿出来, 副档名改成 zip, 然后解压, 找到里面的 libffi_example.so 文件, 用 readelf 命令读取一下文件看看, 再把另一个 so 文件用 readelf 读取一下看看内容
```bash
readelf -d libffi_example.so

Dynamic section at offset 0x3fc70 contains 25 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so]
 0x000000000000001a (FINI_ARRAY)         0x3eb90
 0x000000000000001c (FINI_ARRAYSZ)       16 (bytes)
 0x0000000000000004 (HASH)               0x1c8
 0x000000006ffffef5 (GNU_HASH)           0x3a0
 0x0000000000000005 (STRTAB)             0x7a0
 0x0000000000000006 (SYMTAB)             0x3c8
 0x000000000000000a (STRSZ)              412 (bytes)
 0x000000000000000b (SYMENT)             24 (bytes)
 0x0000000000000003 (PLTGOT)             0x40e40
 0x0000000000000002 (PLTRELSZ)           864 (bytes)
 0x0000000000000014 (PLTREL)             RELA
 0x0000000000000017 (JMPREL)             0x3688
 0x0000000000000007 (RELA)               0x9d0
 0x0000000000000008 (RELASZ)             11448 (bytes)
 0x0000000000000009 (RELAENT)            24 (bytes)
 0x000000000000001e (FLAGS)              BIND_NOW
 0x000000006ffffffb (FLAGS_1)            Flags: NOW
 0x000000006ffffffe (VERNEED)            0x990
 0x000000006fffffff (VERNEEDNUM)         2
 0x000000006ffffff0 (VERSYM)             0x93c
 0x000000006ffffff9 (RELACOUNT)          476
 0x0000000000000000 (NULL)               0x0
```

```bash
readelf -d libffidemo.so

Dynamic section at offset 0x32aa8 contains 27 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [~/FFIDemo/app/src/main/cpp/lib/arm64-v8a/libffi_example.so]
 0x0000000000000001 (NEEDED)             Shared library: [liblog.so]
 0x0000000000000001 (NEEDED)             Shared library: [libm.so]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so]
 0x000000000000000e (SONAME)             Library soname: [libffidemo.so]
 0x000000000000001a (FINI_ARRAY)         0x30d50
 0x000000000000001c (FINI_ARRAYSZ)       16 (bytes)
 0x000000006ffffef5 (GNU_HASH)           0x228
 0x0000000000000005 (STRTAB)             0x36a0
 0x0000000000000006 (SYMTAB)             0xe68
 0x000000000000000a (STRSZ)              8493 (bytes)
 0x000000000000000b (SYMENT)             24 (bytes)
 0x0000000000000003 (PLTGOT)             0x33c98
 0x0000000000000002 (PLTRELSZ)           1848 (bytes)
 0x0000000000000014 (PLTREL)             RELA
 0x0000000000000017 (JMPREL)             0xd8d8
 0x0000000000000007 (RELA)               0x5b68
 0x0000000000000008 (RELASZ)             32112 (bytes)
 0x0000000000000009 (RELAENT)            24 (bytes)
 0x000000000000001e (FLAGS)              BIND_NOW
 0x000000006ffffffb (FLAGS_1)            Flags: NOW
 0x000000006ffffffe (VERNEED)            0x5b28
 0x000000006fffffff (VERNEEDNUM)         2
 0x000000006ffffff0 (VERSYM)             0x57ce
 0x000000006ffffff9 (RELACOUNT)          886
 0x0000000000000000 (NULL)               0x0
```

然后你会发觉, Rust 生成的动态库, 没有 Library soname, 所以我们得再生成一个带 soname 的动态库. 回到 Rust 项目, 通过下面的命令构建一下, 可以先 `cargo clean` 清理一下 **target** 目录

```bash
cargo clean

RUSTFLAGS="-Clink-arg=-Wl,-soname=libffi_example.so" cargo build --target aarch64-linux-android --release
```

这里我们手动给动态库加上了 soname, 再把生成的动态库放到 Android 工程中. 在重新执行之前, 可以把项目中 app 目录下的 .cxx 跟 build 文件夹删一下, 防止出现奇怪的问题. 再次执行时, 我们的 App 已经可以跑起来. 接着把 native-lib 的 Cpp 代码 stringFromJNI, 修改一下, 用用看原生库的效果, 因为现在仿真器的屏幕上显示得还是 `Hello from C++`.

```cpp
#include <jni.h>
#include <string>

#include "llmd5.h"

extern "C" JNIEXPORT jstring JNICALL
Java_wiki_mdzz_ffidemo_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    auto fooMD5 = ll_md5("foo");
    return env->NewStringUTF(fooMD5);
}
```

我们还忘了把头文件加上, 头文件内容长这样

```cpp
#ifndef FFIDEMO_LLMD5_H
#define FFIDEMO_LLMD5_H

#if __cplusplus
extern "C" {
#endif

const char *ll_md5(const char *buf);

#if __cplusplus
}
#endif

#endif //FFIDEMO_LLMD5_H
```
因为我们原生语言用得是 **Cpp**, 所以需要加上 `extern "C"`.

然后再编译执行 App, 应该能看到仿真器的屏幕上显示一串字符串. 为了让这个函数更通用, 可以接收 **Java/Kotlin** 那边传过来的字符串, 再生成对应的 md5 字符串.

```cpp
#include <jni.h>
#include <string>

#include "llmd5.h"

extern "C" JNIEXPORT jstring JNICALL
Java_wiki_mdzz_ffidemo_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */, jstring buf) {
    auto data = env->GetStringUTFChars(buf, nullptr);
    auto result = ll_md5(data);
    env->ReleaseStringUTFChars(buf, data);
    return env->NewStringUTF(result);
}
```

**Kotlin/Java** 的代码也可以改一下

```kotlin
private external fun stringFromJNI(buf: String): String
```

```java
public native String stringFromJNI(String buf);
```
----

不想看文字, 可以直接看项目 https://e.coding.net/limitLiu/java/FFIDemo.git
