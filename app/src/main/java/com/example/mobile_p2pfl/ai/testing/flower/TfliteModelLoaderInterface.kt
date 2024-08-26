
package com.example.mobile_p2pfl.ai.temp

import com.example.mobile_p2pfl.ai.testing.TfLiteModel
import java.io.IOException

// Interface for loading a TFLite model.
interface TfliteModelLoaderInterface {


  @Throws(IOException::class)
  fun loadTfliteModel(): TfLiteModel


}
