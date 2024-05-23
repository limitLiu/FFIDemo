package wiki.mdzz.ffidemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import rust.ffi.Demo
import wiki.mdzz.ffidemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val demo = Demo()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sampleText.text = demo.md5("foo")
        binding.arrayText.text = demo.transform(floatArrayOf(1F, 2F, 3F)).stringify()
    }
}

fun FloatArray.stringify(): String {
    val sb = StringBuilder()
    sb.append("[")
    for (i in this) {
        sb.append(i)
        sb.append(", ")
    }
    sb.append("]")
    return sb.toString()
}