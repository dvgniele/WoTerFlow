package wot.events

enum class EventType(val event: String) {
    THING_CREATED("thing_created"),
    THING_UPDATED("thing_updated"),
    THING_DELETED("thing_deleted");

    companion object {
        fun fromString(event: String): EventType? {
            return values().find { it.event == event }
        }
    }
}