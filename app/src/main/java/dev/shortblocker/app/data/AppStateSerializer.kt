package dev.shortblocker.app.data

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object AppStateSerializer : Serializer<AppState> {
    override val defaultValue: AppState = AppState()

    override suspend fun readFrom(input: InputStream): AppState {
        val content = input.readBytes().decodeToString()
        return runCatching { AppStateJsonCodec.decode(content) }
            .getOrElse { AppState() }
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(AppStateJsonCodec.encode(t).encodeToByteArray())
    }
}
