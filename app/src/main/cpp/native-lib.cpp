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