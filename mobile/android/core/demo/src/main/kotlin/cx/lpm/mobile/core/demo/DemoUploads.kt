package cx.lpm.mobile.core.demo

import java.util.UUID

/**
 * Demo handlers for the chunked attach (videos and files from the composer). Nothing is
 * kept but the name, and each piece is acknowledged as if written.
 */
internal fun DemoServer.registerUploadHandlers() {
    register("uploadBegin") { o ->
        val id = o.string("id") ?: return@register
        val uploadId = UUID.randomUUID().toString().uppercase()
        world.uploadNames[uploadId] = o.string("name") ?: "file"
        pushAfter(
            0.2,
            mapOf(
                "t" to "uploadBegin",
                "id" to id,
                "ok" to true,
                "uploadId" to uploadId,
                "chunk" to (1 shl 20),
                "reqId" to o["reqId"]
            )
        )
    }
    register("uploadChunk") { o ->
        val id = o.string("id") ?: return@register
        val offset = o["offset"].numberValue() ?: 0
        val length = decodeBase64(o.string("b64") ?: "")?.size?.toLong() ?: 0
        pushAfter(
            0.05,
            mapOf(
                "t" to "uploadChunk",
                "id" to id,
                "ok" to true,
                "received" to offset + length,
                "reqId" to o["reqId"]
            )
        )
    }
    register("uploadDone") { o ->
        val id = o.string("id") ?: return@register
        val name = world.uploadNames.remove(o.string("uploadId") ?: "") ?: "file"
        pushAfter(
            0.2,
            mapOf(
                "t" to "uploadDone",
                "id" to id,
                "ok" to true,
                "path" to "/Users/demo/uploads/$name",
                "reqId" to o["reqId"]
            )
        )
    }
    register("uploadAbort") { o ->
        world.uploadNames.remove(o.string("uploadId") ?: "")
    }
}
