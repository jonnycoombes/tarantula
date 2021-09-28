package facade

import akka.actor.ActorSystem
import anorm.{Macro, RowParser}
import org.joda.time.DateTime
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json.JodaWrites.jodaDateWrites
import play.api.libs.json.{JsValue, Json, Writes}

import javax.inject.{Inject, Singleton}

/**
 * Contains any cross-cutting/global stuff relating to the db context
 */
package object db {

  /**
   * A [[CustomExecutionContext]] for use by [[DbContext]] implementations, so that SQL operations are performed within their own thread
   * pool
   *
   * @param system an injected [[ActorSystem]]
   */
  @Singleton
  class DbExecutionContext @Inject()(system: ActorSystem) extends CustomExecutionContext(system, "facade.sql.dispatcher")

  /**
   * A list of node subtypes who have a corresponding volume
   */
  lazy val VolumeSubTypes: Seq[Long] = List[Long](848)

  /**
   * Case class for containing core node database details (taken from DTreeCore)
   *
   * @param parentId     the parent id
   * @param dataId       the node data id
   * @param versionNum   the current version number of the node
   * @param name         the name of the node
   * @param subType      the subtype of the node
   * @param originDataId the origin data id for an alias
   * @param createDate   the creation date for the node
   * @param modifyDate   the last modification date for the node
   */
  case class NodeCoreDetails(parentId: Long,
                             dataId: Long,
                             versionNum: Long,
                             name: String,
                             subType: Long,
                             originDataId: Long,
                             createDate: DateTime,
                             modifyDate: DateTime) {
    /**
     * Returns true if the underlying node is an alias
     * @return
     */
    @inline def isAlias: Boolean = subType == 1

    /**
     * Returns true if the underlying node is a volume
     * @return
     */
    @inline def isVolume: Boolean = VolumeSubTypes.contains(subType)

    /**
     * Returns true if the underlying node is a document
     * @return
     */
    @inline def isDocument: Boolean = subType == 144

    /**
     * Returns true if the underlying node is a folder
     * @return
     */
    @inline def isFolder: Boolean = subType == 0
  }

  /**
   * Case class for wrapping a subset of details from DVersData
   * @param versionId the unique version id
   * @param version the version number
   * @param createDate the creation date for the version
   * @param modifyDate the modify date for the version
   * @param fileCreateDate the underlying file creation date
   * @param fileModifyDate the underlying file last modified date
   * @param filename the filename associated with the version
   * @param size the size of the version in bytes
   * @param mimeType the MIME type of the version
   */
  case class NodeVersionDetails(versionId: Long,
                                version : Long,
                                createDate : DateTime,
                                modifyDate : DateTime,
                                fileCreateDate : DateTime,
                                fileModifyDate : DateTime,
                                filename : String,
                                size: Long,
                                mimeType : Option[String])

  /**
   * Case class wrapper for results against the bespoke Facade views within the database. Basically, an instance of this case class will
   * be created for each category/attribute pair relating to a given node
   * @param category the name of the category
   * @param attribute the name of the attribute
   * @param attributeType the integer type code for the attribute
   * @param valDate the optional date value
   * @param valInt the optional integer value
   * @param valLong the optional long value
   * @param valReal the optional real value
   * @param valString the optional string value
   */
  case class NodeAttributeDetails(category : String,
                                  attribute : String,
                                  attributeType : Int,
                                  valDate : Option[DateTime],
                                  valInt : Option[Int],
                                  valLong : Option[Long],
                                  valReal : Option[Double],
                                  valString : Option[String])

  /**
   * A combined structure that contains all of the relevant DB information relating to a given underlying node structure
   */
  case class NodeDetails(core : NodeCoreDetails,
                         versions : List[NodeVersionDetails],
                         attributes : List[NodeAttributeDetails]) {

    /**
     * Returns true if the node has any versions
     * @return *true* or *false*
     */
    @inline def hasVersions : Boolean = !versions.isEmpty

    /**
     * Returns true if the node has any attributes
     * @return *true* or *false*
     */
    @inline def hasAttributes : Boolean = !attributes.isEmpty

  }

  /**
   * Used for formatting Joda date time structures
   */
  implicit lazy val dateWriter: Writes[DateTime] = jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSSZ'")

  /**
   * [[Writes]] for [[NodeCoreDetails]]
   */
  implicit val nodeCoreDetailsWrites: Writes[NodeCoreDetails] = (o: NodeCoreDetails) => {
    Json.obj(
      "parentId" -> o.parentId,
      "dataId" -> o.dataId,
      "versionNum" -> o.versionNum,
      "name" -> o.name,
      "subType" -> o.subType,
      "originDataId" -> o.originDataId,
      "createDate" -> o.createDate,
      "modifyDate" -> o.modifyDate,
      "isAlias" -> o.isAlias,
      "isVolume" -> o.isVolume,
      "isDocument" -> o.isDocument,
      "isFolder" -> o.isFolder
    )
  }

  /**
   * [[Writes]] for [[NodeVersionDetails]]
   */
  implicit val nodeVersionDetailsWrites: Writes[NodeVersionDetails] = (v: NodeVersionDetails) => {
    Json.obj(
      "versionId" -> v.versionId,
      "version" -> v.version,
      "createDate"-> v.createDate,
      "modifyDate" -> v.modifyDate,
      "fileCreateDate" -> v.fileCreateDate,
      "fileModifyDate" -> v.fileModifyDate,
      "filename" -> v.filename,
      "size" -> v.size,
      "mimeType" -> v.mimeType
    )
  }

  /**
   * Parser for handling DTreeCore subset
   */
  lazy val nodeCoreDetailsParser: RowParser[NodeCoreDetails] = Macro.parser[NodeCoreDetails]("ParentID", "DataID", "VersionNum", "Name",
    "SubType", "OriginDataID", "CreateDate", "ModifyDate")

  /**
   * Parser for handling DVersData subset
   */
  lazy val nodeVersionDetailsParser : RowParser[NodeVersionDetails] = Macro.parser[NodeVersionDetails]("VersionID", "Version",
    "VerCDate", "VerMDate", "FileCDate", "FileMDate", "FileName", "DataSize", "MimeType")

  /**
   * Parser for handling query results against the Facade_Attributes view
   */
  lazy val nodeAttributeDetailsParser : RowParser[NodeAttributeDetails] = Macro.parser[NodeAttributeDetails]("Category", "Attribute",
    "AttrType", "ValDate", "ValInt", "ValLong", "ValReal", "ValStr")

}
