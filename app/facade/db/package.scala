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


  case class NodeVersionDetails(dummy: String)

  case class NodeAttributeDetails(dummy : String)

  /**
   * A combined structure that contains all of the relevant DB information relating to a given underlying node structure
   */
  case class NodeDetails(core : NodeCoreDetails, versions : List[NodeVersionDetails], attributes : List[NodeAttributeDetails])

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
   * Parser for handling DTreeCore subset
   */
  lazy val nodeCoreDetailsParser: RowParser[NodeCoreDetails] = Macro.parser[NodeCoreDetails]("ParentID", "DataID", "VersionNum", "Name",
    "SubType", "OriginDataID", "CreateDate", "ModifyDate")

}
