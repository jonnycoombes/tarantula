package facade

import akka.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json.{JsValue, Json, Writes}

import javax.inject.{Inject, Singleton}

/**
 * Contains any cross-cutting/global stuff relating to the db context
 */
package object db {

  /**
   * A [[CustomExecutionContext]] for use by [[DbContext]] implementations, so that SQL operations are performed within their own thread
   * pool
   * @param system an injected [[ActorSystem]]
   */
  @Singleton
  class DbExecutionContext @Inject()(system : ActorSystem) extends CustomExecutionContext(system, "facade.sql.dispatcher")

  /**
   * Case class for containing core node database details (taken from DTreeCore)
   * @param parentId the parent id
   * @param dataId the node data id
   * @param name the name of the node
   * @param subType the subtype of the node
   * @param originDataId the origin data id for an alias
   */
  case class NodeCoreDetails(parentId : Long, dataId : Long, name : String, subType : Long, originDataId : Long)

  /**
   * [[Writes]] for [[NodeCoreDetails]]
   */
  implicit val nodeCoreDetailsWrites : Writes[NodeCoreDetails] = new Writes[NodeCoreDetails] {
    override def writes(o: NodeCoreDetails): JsValue = {
      Json.obj(
        "parentId" -> o.parentId,
        "dataId" -> o.dataId,
        "name" -> o.name,
        "subType" -> o.subType,
        "originDataId" -> o.originDataId
      )
    }
  }

}
