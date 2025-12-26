package org.example.app

import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.Loader
import java.nio.file.Paths

fun main() {
    val protoFilePath = "path/to/your/file.proto" // Adjust this path

    try {
        val schema = Loader()
            .addDirectory(Paths.get(protoFilePath).parent)
            .schema()

        println(schema)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
