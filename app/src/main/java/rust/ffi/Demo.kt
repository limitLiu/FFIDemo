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