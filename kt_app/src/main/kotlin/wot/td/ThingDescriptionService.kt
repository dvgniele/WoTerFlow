package wot.td

import com.fasterxml.jackson.databind.node.ObjectNode
import errors.ValidationError
import exceptions.ConversionException
import exceptions.ThingException
import exceptions.ValidationException
import io.ktor.server.plugins.*
import org.apache.jena.query.*
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.Lang
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.update.UpdateProcessor
import utils.RDFConverter
import utils.Utils
import wot.directory.DirectoryConfig
import wot.search.sparql.SparqlService
import java.time.Instant
import java.util.*

/**
 * Service responsible for managing [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TDs) within the system.
 *
 * @param dbRdf The RDF dataset used for querying and storing [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td).
 * @param thingsMap A mutable map containing Thing IDs as keys and corresponding Thing Descriptions as values.
 */
class ThingDescriptionService(dbRdf: Dataset, private val thingsMap: MutableMap<String, ObjectNode>) {
    private val rdfDataset: Dataset = dbRdf

    private var ttlContextModel: Model = Utils.loadModel(DirectoryConfig.ttlContextLocal, Lang.TURTLE)
    private var xmlShapesModel: Model = Utils.loadModel(DirectoryConfig.xmlShapesLocal, Lang.RDFXML)

    private val converter = RDFConverter()

    /**
     * Refreshes the `JSON` representation of a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TD) in the in-memory cache.
     *
     * @param graphId The [UUID] of the Thing Description in the RDF [Dataset].
     */
    private fun refreshJsonDbItem(graphId: String) {
        try {
            val ttlModel = Utils.loadRDFModelById(rdfDataset, graphId)

            if (!ttlModel.isEmpty){
                val objNode = converter.fromRdf(ttlModel)
                val thing = converter.toJsonLd11(Utils.toJson(objNode.toString()))

                //  Update the in-memory cache with the refreshed JSON representation
                thingsMap[graphId] = thing
            } else {
                thingsMap.remove(graphId)
            }
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb item with id: $graphId: ${e.message}")
        }
    }

    /**
     * Refreshes the `JSON` representations of all the [Thing Descriptions](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TDs) in the in-memory cache.
     **/
    fun refreshJsonDb() {
        rdfDataset.begin(TxnType.READ)

        try {
            val ttlList = Utils.loadRDFDatasetIntoModelList(rdfDataset)
            val things = ttlList.map { converter.toJsonLd11(converter.fromRdf(it)) }

            //  Clear the things map and populate it back with the updated dataset
            thingsMap.clear()


            //  Update the in-memory cache with the refreshed JSON representation
            things.forEach { thing ->
                thing["id"]?.asText()?.let { id ->
                    thingsMap[Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)] = thing
                }
            }

            println("\t###\tREADY\t###")
        } catch (e: Exception) {
            throw ThingException("Error refreshing the JsonDb: ${e.message}")
        } finally {
            rdfDataset.end()
        }
    }


    /**
     * Inserts an Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) into the RDF [Dataset] and returns the generated [UUID] along with the TD.
     *
     * This function takes an anonymous TD represented as a JSON object, inserts it into the RDF [Dataset],
     * and generates a unique ID for the TD. The generated ID is returned along with the updated TD containing
     * the assigned ID.
     *
     * @param td The Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be inserted.
     *
     * @return A [Pair] containing the generated [UUID] and the updated TD with the assigned [UUID].
     * @throws BadRequestException If the request is invalid.
     * @throws ThingException If an error occurs during the insertion process.
     * @throws ValidationException If an error occurs during the validation process.
     * @throws ConversionException If an error occurs during the conversion process
     */
    fun insertAnonymousThing(td: ObjectNode): Pair<String, ObjectNode> {
        rdfDataset.begin(TxnType.WRITE)

        var query = ""

        var uuid = UUID.randomUUID().toString()
        var id = Utils.strconcat("urn:uuid:", uuid)
        var graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            while (Utils.idExists(thingsMap.keys, graphId)) {
                uuid = UUID.randomUUID().toString()
                id = Utils.strconcat("urn:uuid:", uuid)
                graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            }

            td.put("@id", id)

            // Checking the jsonld version and upgrading if needed
            val tdVersion11p = Utils.isJsonLd11OrGreater(td)

            val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

            // JsonLd decoration with missing fields
            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.toRdf(tdV11.toString())

            //  Performing Syntactic Validation
            val syntacticValidationFailures =
                ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

            if (syntacticValidationFailures.isNotEmpty()) {
                val validationErrors = syntacticValidationFailures.map {
                    ValidationError(
                        "Syntactic Validation",
                        it
                    )
                }
                throw ValidationException(validationErrors, "Syntactic Validation Failed")
            }

            //  Performing Semantic Validation
            val semanticValidationFailures =
                ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

            if (semanticValidationFailures.isNotEmpty()) {
                val validationErrors = semanticValidationFailures.map {
                    ValidationError(
                        "Semantic Validation",
                        it
                    )
                }
                throw ValidationException(validationErrors, "Semantic Validation Failed")
            }


            // Query preparation for RDF data storing
            val rdfTriplesString = converter.toString(jsonRdfModel)
            query = """
                INSERT DATA {
                    GRAPH <$graphId> {
                        $rdfTriplesString
                    }
                }
            """.trimIndent()

            //  Execute query
            SparqlService.update(query, rdfDataset)

            synchronized(this) {
                //  Commit to close db connection
                rdfDataset.commit()
            }

            return Pair(id, tdV11)

        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while storing the thing: ${e.message}")
        } catch (e: ValidationException) {
            rdfDataset.abort()

            throw e
        } catch (e: ConversionException) {
            rdfDataset.abort()

            throw ConversionException("Create Anonymous Thing Error: ${e.message}")
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Create new Thing Error: ${e.message}\nquery:\n$query")
        } finally {
            rdfDataset.end()

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(graphId)
            rdfDataset.end()
        }
    }

    /**
     * Creates or Updates (if already existing) a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) into the RDF [Dataset] and returns the updated [UUID] along with the TD.
     *
     * This function takes a TD represented as a JSON object, inserts or updates it into the RDF [Dataset].
     * The [UUID] is returned along with the updated TD containing the assigned [UUID].
     *
     * @param td The Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be inserted.
     *
     * @return A [Pair] containing the generated [UUID] and the updated TD with the assigned [UUID].
     * @throws BadRequestException If the request is invalid.
     * @throws ThingException If an error occurs during the insertion process.
     * @throws ValidationException If an error occurs during the validation process.
     * @throws ConversionException If an error occurs during the conversion process
     */
    fun updateThing(td: ObjectNode): Pair<String, Boolean> {
        val id: String = td.get("@id")?.takeIf { it.isTextual }?.asText()
            ?: td.get("id")?.takeIf { it.isTextual }?.asText()
            ?: throw BadRequestException("Invalid or missing @id field in the JSON body.")

        rdfDataset.begin(TxnType.WRITE)

        var query = ""
        var existsAlready = false
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            existsAlready = checkIfThingExists(id)

            val tdVersion11p = Utils.isJsonLd11OrGreater(td)
            val tdV11 = if (!tdVersion11p) converter.toJsonLd11(td) else td

            decorateThingDescription(tdV11)

            val jsonRdfModel = converter.toRdf(tdV11.toPrettyString())

            //  Performing Syntactic Validation
            val syntacticValidationFailures =
                ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

            if (syntacticValidationFailures.isNotEmpty()) {
                val validationErrors = syntacticValidationFailures.map {
                    ValidationError(
                        "Syntactic Validation",
                        it
                    )
                }
                throw ValidationException(validationErrors, "Syntactic Validation Failed")
            }

            //  Performing Semantic Validation
            val semanticValidationFailures =
                ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

            if (semanticValidationFailures.isNotEmpty()) {
                val validationErrors = semanticValidationFailures.map {
                    ValidationError(
                        "Semantic Validation",
                        it
                    )
                }
                throw ValidationException(validationErrors, "Semantic Validation Failed")
            }

            // Query preparation for RDF data storing
            val rdfTriplesString = converter.toString(jsonRdfModel)
            query = """
                DELETE WHERE {
                    GRAPH <$graphId> {
                        ?s ?p ?o
                    }
                };
                INSERT DATA {
                    GRAPH <$graphId> {
                        $rdfTriplesString
                    }
                }
            """.trimIndent()

            //  Execute query
            val update = UpdateFactory.create(query)
            val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
            updateExecution.execute()

            synchronized(this) {
                //  Commit to close db connection
                rdfDataset.commit()
            }

            return Pair(id, existsAlready)
        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while updating the thing: ${e.message}")
        } catch (e: ValidationException) {
            rdfDataset.abort()

            throw e
        } catch (e: ConversionException) {
            rdfDataset.abort()

            throw ConversionException("Update Thing Error: ${e.message}")
        }catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Update Thing Error: ${e.message}")
        } finally {
            rdfDataset.end()

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(graphId)
            rdfDataset.end()
        }
    }

    /**
     * Partially Updates a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) into the RDF [Dataset] and returns the updated [UUID].
     *
     * @param td The Anonymous [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be inserted.
     *
     * @return A [String] containing the [UUID] of the updated TD.
     * @throws BadRequestException If the request is invalid.
     * @throws ThingException If an error occurs during the insertion process.
     * @throws ValidationException If an error occurs during the validation process.
     * @throws ConversionException If an error occurs during the conversion process
     */
    fun patchThing(td: ObjectNode, id: String): String {
        rdfDataset.begin(TxnType.WRITE)

        var query = ""
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            val thing = retrieveThingById(id)

            if (thing != null) {
                thing.setAll<ObjectNode>(td)

                removeEmptyProperties(thing)

                decorateThingDescription(thing)

                val jsonRdfModel = converter.toRdf(thing.toString())

                //  Performing Syntactic Validation
                val syntacticValidationFailures =
                    ThingDescriptionValidation.validateSyntactic(jsonRdfModel, xmlShapesModel)

                if (syntacticValidationFailures.isNotEmpty()) {
                    val validationErrors = syntacticValidationFailures.map {
                        ValidationError(
                            "Syntactic Validation",
                            it
                        )
                    }
                    throw ValidationException(validationErrors, "Syntactic Validation Failed")
                }

                //  Performing Semantic Validation
                val semanticValidationFailures =
                    ThingDescriptionValidation.validateSemantic(jsonRdfModel, ttlContextModel)

                if (semanticValidationFailures.isNotEmpty()) {
                    val validationErrors = semanticValidationFailures.map {
                        ValidationError(
                            "Semantic Validation",
                            it
                        )
                    }
                    throw ValidationException(validationErrors, "Semantic Validation Failed")
                }

                // Query preparation for RDF data storing
                val rdfTriplesString = converter.toString(jsonRdfModel)
                query = """
                    DELETE WHERE {
                        GRAPH <$graphId> {
                            ?s ?p ?o
                        }
                    };
                    INSERT DATA {
                        GRAPH <$graphId> {
                            $rdfTriplesString
                        }
                    }
                """.trimIndent()

                //  Execute query
                val update = UpdateFactory.create(query)
                val updateExecution: UpdateProcessor = UpdateExecutionFactory.create(update, rdfDataset)
                updateExecution.execute()

                synchronized(this) {
                    //  Commit to close db connection
                    rdfDataset.commit()
                }

                return id
            } else {
                throw ThingException("Thing with id: $graphId does not exists.")
            }

        } catch (e: ThingException) {
            rdfDataset.abort()

            throw ThingException("An error occurred while patching the thing: ${e.message}")
        } catch (e: ValidationException) {
            rdfDataset.abort()

            throw e
        } catch (e: ConversionException) {
            rdfDataset.abort()

            throw ConversionException("Patch Thing Error: ${e.message}")
        }catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("Patch Thing Error: ${e.message}")
        } finally {
            rdfDataset.end()

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(id)
            rdfDataset.end()
        }
    }

    /**
     * Deletes a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TD) from the RDF [Dataset] based on the specified [UUID].
     *
     * @param id The ID of the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be deleted.
     *
     * @throws ThingException If an error occurs during the deletion process.
     */
    fun deleteThingById(id: String) {
        rdfDataset.begin(TxnType.WRITE)
        val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)

        try {
            val deleteQuery = "DELETE WHERE { GRAPH <$graphId> { ?s ?p ?o } }"

            val deleteUpdate = UpdateFactory.create(deleteQuery)
            val deleteExecution: UpdateProcessor = UpdateExecutionFactory.create(deleteUpdate, rdfDataset)
            deleteExecution.execute()

            synchronized(this) {
                //  Commit to close db connection
                rdfDataset.commit()
            }
        } catch (e: Exception) {
            rdfDataset.abort()

            throw ThingException("An error occurred while Deleting the thing: ${e.message}")
        } finally {
            rdfDataset.end()

            rdfDataset.begin(TxnType.READ)
            refreshJsonDbItem(graphId)
            rdfDataset.end()
        }
    }

    /**
     * Retrieves a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) (TD) from the in-memory cache based on the specified [UUID].
     *
     * @param id The [UUID] of the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be retrieved.
     *
     * @return The retrieved [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) as an [ObjectNode], or null if the [UUID] is not found.
     * @throws ThingException If an error occurs during the retrieval process.
     */
    fun retrieveThingById(id: String): ObjectNode? {
        try {
            val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            return thingsMap[graphId]
        } catch (e: Exception){
            throw ThingException("Retrieve Get: ${e.message}")
        }
    }

    /**
     * Checks if a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) with the specified [UUID] exists in the in-memory cache.
     *
     * @param id The [UUID] of the [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be checked for existence.
     *
     * @return `true` if the Thing exists, `false` otherwise.
     * @throws ThingException If an error occurs during the retrieval process.
     */
    fun checkIfThingExists(id: String): Boolean {
        try {
            val graphId = Utils.strconcat(DirectoryConfig.GRAPH_PREFIX, id)
            return thingsMap.containsKey(graphId)
        } catch (e: Exception){
            throw ThingException("Retrieve Head: ${e.message}")
        }
    }

    /**
     * Retrieves a list of all Things from the in-memory cache.
     *
     * This function retrieves all Things stored in the in-memory cache and returns them as a list of ObjectNodes.
     *
     * @return A list of ObjectNodes representing all Things in the in-memory cache.
     * @throws ThingException If an error occurs during the retrieval process.
     */
    fun retrieveAllThings(): List<ObjectNode> {
        try {
            return thingsMap.values.toList()
        } catch (e: Exception){
            throw ThingException("Retrieve All: ${e.message}")
        }
    }

    /**
     * Decorates a [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) with additional information.
     *
     * @param td The original [Thing Description](https://www.w3.org/TR/wot-thing-description/#introduction-td) to be decorated.
     */
    private fun decorateThingDescription(td: ObjectNode) {
        if (!td.has("@type")) {
            val typeArray = td.putArray("@type")
            typeArray.add("Thing")
        } else if (!Utils.hasThingType(td)) {
            val typeArray = td.withArray("@type")
            typeArray.add("Thing")
        }

        //  Add registration info: "created" if not already existing
        val registrationInfo: ObjectNode = if (!td.has("registration")) {
            Utils.jsonMapper.createObjectNode().put("created", Instant.now().toString())
        } else {
            td["registration"] as ObjectNode
        }

        //  Add registration info: "modified" with the current timestamp
        registrationInfo.put("modified", Instant.now().toString())
        td.set<ObjectNode>("registration", registrationInfo)
    }

    /**
     * Removes empty properties from an [ObjectNode].
     *
     * @param objectNode The ObjectNode to remove empty properties from.
     */
    private fun removeEmptyProperties(objectNode: ObjectNode) {
        val fieldsToRemove = mutableListOf<String>()

        objectNode.fields().forEach { (fieldName, fieldValue)  ->
            if (fieldValue.isNull) {
                fieldsToRemove.add(fieldName)
            } else if (fieldValue.isObject) {
                removeEmptyProperties(fieldValue as ObjectNode) //  Recursive call for nested objects
            }
        }

        fieldsToRemove.forEach {
            objectNode.remove(it)
        }
    }
}