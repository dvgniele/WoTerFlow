package wot.directory

import org.apache.jena.query.Dataset
import wot.td.ThingDescriptionController

class Directory(db: Dataset, controller: ThingDescriptionController) {
    val db = db
    val thingController = controller
}