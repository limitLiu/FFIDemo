# Android use Shared library which the Rust generated.

**Android NDK** 可以使用一些第三方的动态库, 如何用 **Rust** 写个东西生成动态库, 给 **Android** 这边调用,
这边记录一下过程.

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

[lib]
name = "ffi_example"
crate-type = ["cdylib"]

[dependencies]
md5 = "0.7"
jni = "0.21.1"
```

为了把 **Rust** 生成的动态库给 **Android** 端使用，这个工程主要是使用到了一个 `md5` 的 **crate**
来处理生成 md5 字符串，通过 `jni` **crate** 来处理跟 **Java/Kotlin** 的互操作.

然后跳到工程中的 lib.rs 文件, 把里面的内容改成下面这些

```rust
use jni::{
  objects::{JClass, JFloatArray, JString},
  sys::{jfloatArray, jint, jstring},
  JNIEnv,
};
use md5::compute;

#[no_mangle]
pub extern "system" fn Java_rust_ffi_Demo_md5(mut env: JNIEnv, _: JClass, buf: JString) -> jstring {
  let input: String = match env.get_string(&buf) {
    Ok(x) => x.into(),
    Err(err) => {
      env.exception_clear().expect("clear");
      env
        .throw_new("java/lang/Exception", format!("{err:?}"))
        .expect("throw");
      return std::ptr::null_mut();
    }
  };
  let output = env
    .new_string(format!("{:x}", compute(input)))
    .expect("Failed to new string");
  output.into_raw()
}
```

这里代码主要是接收一个 Java/Kotlin 的字符串，然后转成 Rust 的字符串，再用 `md5` 这个 **crate** 生成
md5 字符串并 `return` 出去。

代码可以加点自己的 lint, 可以补充个 rustfmt.toml 文件, 譬如我这里用得是两个空格的代码风格

```toml
tab_spaces = 2
```

现在把我们的代码构建成动态库, 可以把对应 **x86** 的 **target** 安装上, 安装对应的 **target**, 需要用
**rustup** 安装, 可以先搜索一下有哪些 **target**

```bash
rustup target list
```

如果你用得是水果 **M1** 芯片的设备, 可以直接使用 ARM64 的 Android 仿真器, 下面这条命令就可以兼顾
Android 真机跟 M1 上的 Android 仿真器 (只要你 Android Studio 设置的仿真器是 ARM64 的)

```bash
rustup target add aarch64-linux-android
```

假设你已经装好了必要的 **target**, 可以执行下面的命令打包

```bash
cargo build --target aarch64-linux-android --release
```

然后我们看到工程的 target 文件夹下生成了一个 **aarch64-linux-android** 文件夹, 里面的 **release**
文件夹下就有我们想要的 **libffi_example.so** 文件

### 如果编译出错

其实还有一个事情没讲, 那就是 Rust 编译 Android 可用的动态库, 需要配置 NDK.
先把 ndk 装好, 直接在 Android Studio SDK Tools 的 NDK (Side by side) 选一个版本安装.

然后执行下面的命令, 具体目录根据自己的情况而定

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export NDK=$ANDROID_HOME/sdk/ndk/26.3.11579264
cd ~/Library/Android/sdk/ndk
ln -s 26.3.11579264 version # 软链接对应版本
```

上面只处理 arm64 的情况, 具体 ABI 根据自己的需要而定, 然后设置一下 ~/.cargo/config.toml 里面的内容

```toml
[target.aarch64-linux-android]
ar = "/Users/your name/Library/Android/sdk/ndk/version/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
linker = "/Users/your name/Library/Android/sdk/ndk/version/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android28-clang"

[target.armv7-linux-androideabi]
ar = "/Users/your name/Library/Android/sdk/ndk/version/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
linker = "/Users/your name/Library/Android/sdk/ndk/version/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi28-clang"

[target.i686-linux-android]
ar = "/Users/your name/Library/Android/sdk/ndk/version/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
linker = "/Users/your name/Library/Android/sdk/ndk/version/toolchains/llvm/prebuilt/darwin-x86_64/bin/i686-linux-android28-clang"
```

其实就是根据你指定的 target 使用用对应平台的链接器，这里建议使用 NDK 的版本是 26，因为旧版我已经没测试啦。

## 配置 Android 工程

现在用 Android Studio 来创建个 Android 的项目，模板选择 Cpp 的那个，语言不论 Kotlin 还是 Java 都可以,
Minimum SDK 随便选一个，我这里选得是 API 26 以上的。

然后把之前通过

```bash
cargo build --target aarch64-linux-android --release
```

生成的动态库拷贝到 **Android** 项目中来，直接放到 `app/src/main/jniLibs/arm64-v8a` 目录下(
如果你有其他的 **ABI** 的动态库，你也可以加上对应的文件夹，放入相应的动态库)。
此外，`build.gradle (:app)` 可以把对应的 **ndkVersion** 加上，顺便把创建项目时生成的 Cpp
相关配置删掉，因为已经不需要这碍事的玩意啦！

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    defaultConfig {
        compileSdk 34
        buildToolsVersion = '34.0.0'
        applicationId "wiki.mdzz.ffidemo"
        minSdk 28
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
//        externalNativeBuild {
//            cmake {
//                cppFlags ''
//            }
//        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
//    externalNativeBuild {
//        cmake {
//            path file('src/main/cpp/CMakeLists.txt')
//            version '3.22.1'
//        }
//    }
    buildFeatures {
        viewBinding true
    }
    ndkVersion '26.3.11579264'
    namespace 'wiki.mdzz.ffidemo'
}
```

## Java/Kotlin 代码调整

因为我们在上面的代码里，写得函数名是 `Java_rust_ffi_Demo_md5`，所以我们建一个文件
`app/src/main/java/rust/ffi/Demo.kt`，里面的内容是

```kotlin
package rust.ffi

class Demo {
    external fun md5(buf: String): String

    external fun transform(array: FloatArray): FloatArray

    companion object {
        init {
            System.loadLibrary("ffi_example")
        }
    }
}
```

然后执行跑在手机或者仿真器上看看效果，如果项目用得是 Java，自己尝试改成 Java 的代码就行啦

----

不想看文字，可以直接看项目 https://limitliu.coding.net/public/java/FFIDemo/git/files
