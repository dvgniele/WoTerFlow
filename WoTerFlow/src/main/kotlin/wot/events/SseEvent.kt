package wot.events

/**
 * Represents a Server-Sent Event (SSE) that can be sent to clients for real-time updates.
 *
 * @property data The payload data of the event.
 * @property event The type of the event (optional).
 * @property id The unique identifier of the event (optional)
 */
data class SseEvent(
    val data: String,
    val event: String? = null,
    val id: String? = null
)