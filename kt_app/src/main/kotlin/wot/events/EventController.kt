package wot.events

import io.ktor.util.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class EventController(val thingCreatedSseFlow: MutableSharedFlow<SseEvent>,
                      val thingUpdatedSseFlow: MutableSharedFlow<SseEvent>,
                      val thingDeletedSseFlow: MutableSharedFlow<SseEvent>
) {
    private val pastEvents = CopyOnWriteArrayList<SseEvent>()
    private val idCounter = AtomicLong(0)

    private fun addEvent(eventType: EventType, eventData: String): SseEvent {
        val event = SseEvent(
            data = eventData,
            event = eventType.toString().toLowerCasePreservingASCIIRules(),
            id = idCounter.getAndIncrement().toString()
        )

        pastEvents.add(event)
        println(event.id)
        return event
    }

    fun getPastEvents(lastReceivedId: String?, vararg eventTypes: EventType): List<SseEvent> {
        return lastReceivedId?.let {
            pastEvents.filter { event ->
                (event.id?.toLong() ?: 0) > it.toLong() &&
                        eventTypes.contains(EventType.fromString(event.event.toString()))
            }
        } ?: emptyList()
    }

    suspend fun notify(eventType: EventType, eventData: String) {
        val event = addEvent(eventType, eventData)

        redirectEventFlow(eventType, event)
    }

    private suspend fun redirectEventFlow(eventType: EventType, event: SseEvent) {
        when (eventType) {
            EventType.THING_CREATED -> {
                thingCreatedSseFlow.emit(event)
            }
            EventType.THING_UPDATED -> {
                thingUpdatedSseFlow.emit(event)
            }
            EventType.THING_DELETED -> {
                thingDeletedSseFlow.emit(event)
            }
        }
    }
}