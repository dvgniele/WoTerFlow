package wot.directory

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.jena.query.Dataset
import wot.events.EventController
import wot.td.ThingDescriptionController

/**
 * Represents a [Things Directory](https://www.w3.org/TR/wot-discovery/#exploration-directory).
 *
 * The `Directory` class manages a dataset ([db]), a mutable map of JSON nodes ([jsonMap]),
 * a Thing Description Controller ([thingController]), and an Event Controller ([eventController]).
 *
 * @property db The dataset associated with the directory.
 * @property jsonMap The mutable map containing JSON nodes.
 * @property thingController The controller responsible for managing Thing Descriptions.
 * @property eventController The controller responsible for managing Server-Sent Events.
 */
class Directory(
    val db: Dataset,
    val jsonMap: MutableMap<String, ObjectNode>,
    val thingController: ThingDescriptionController,
    val eventController: EventController,
)
