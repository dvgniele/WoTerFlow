package wot.directory

import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.jena.query.Dataset
import wot.events.EventController
import wot.td.ThingDescriptionController

class Directory(
    val db: Dataset,
    val jsonMap: MutableMap<String, ObjectNode>,
    val thingController: ThingDescriptionController,
    val eventController: EventController,
)
