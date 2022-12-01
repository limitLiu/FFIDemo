package wiki.mdzz.ffidemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import wiki.mdzz.ffidemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Example of a call to a native method
    binding.sampleText.text = stringFromJNI("foo")
  }

  /**
   * A native method that is implemented by the 'ffidemo' native library,
   * which is packaged with this application.
   */
  private external fun stringFromJNI(buf: String): String

  companion object {
    // Used to load the 'ffidemo' library on application startup.
    init {
      System.loadLibrary("ffidemo")
    }
  }
}