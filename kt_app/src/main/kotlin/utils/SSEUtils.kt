package utils

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import wot.events.EventType
import wot.events.SseEvent
import kotlin.time.Duration.Companion.seconds

class SSEUtils {
    companion object {
        suspend fun ApplicationCall.respondSse(eventsList: List<SseEvent>, vararg eventsFlows: Pair<EventType, Flow<SseEvent>>
        ) {
            with(response) {
                header(HttpHeaders.CacheControl, "no-cache")
                header(HttpHeaders.Connection, "keep-alive")
                status(HttpStatusCode.OK)
            }

            try {
                respondBytesWriter(contentType = ContentType.Text.EventStream) {
/*
                    if (eventsList.isEmpty()) {
                        writeStringUtf8("\n")
                        flush()
                    }
*/

                    eventsList.forEach { event ->
                        writeEvent(event)

                        delay(.3.seconds)
                    }

                    eventsFlows.map { it.second }
                        .merge()
                        .collect { event ->
                            writeEvent(event)
                        }
                }
            } catch (e: ChannelWriteException) {
                throw InternalError(e.message)
            }
        }

        private suspend fun ByteWriteChannel.writeEvent(event: SseEvent) {
            val eventString = buildString {
                event.event?.let { append("event: $it\n") }
                event.id?.let { append("id: $it\n") }
                event.data.lines().forEach { append("data: $it\n") }
                append("\n")
            }
            writeStringUtf8(eventString)
            flush()
        }
    }
}