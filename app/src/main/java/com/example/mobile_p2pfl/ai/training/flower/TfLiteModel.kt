
package com.example.mobile_p2pfl.ai.training;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import org.tensorflow.lite.Interpreter;


class TfLiteModel : Closeable {
  private val interpreter: Interpreter

  constructor(model : ByteBuffer) {
    interpreter = Interpreter(model);
  }
  constructor(model:ByteArray): this(convertToDirectBuffer(model)) {}
  constructor( model:MappedByteBuffer): this(model as ByteBuffer) {}

  fun getInterpreter(): Interpreter {
    return interpreter;
  }

  companion object {
    fun convertToDirectBuffer(data: ByteArray): ByteBuffer {
      val result: ByteBuffer = ByteBuffer.allocateDirect(data.size);
      result.order(ByteOrder.nativeOrder());
      result.put(data);
      result.rewind();
      return result;
    }
  }
  override fun close() {
    interpreter.close();
  }
}
