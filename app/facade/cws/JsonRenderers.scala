package facade.cws
import com.opentext.cws.docman.Node
import org.joda.time.DateTime
import play.api.libs.json.JodaWrites._
import play.api.libs.json.{JsBoolean, JsObject, Json, Writes}

/**
 * Json-related helpers for all things CWS.  Mainly [[Writes]] instances for the various different types returned as a result of CWS
 * outcalls
 */
object JsonRenderers {

  /**
   * Render a [[Node]] as a [[JsObject]]
   * @param node the node to render
   * @return
   */
  def renderNodeToJson(node : Node) : JsObject = {
    Json.obj(
      "id"   -> node.getID,
      "name" -> node.getName,
      "created"-> new DateTime(node.getCreateDate.toGregorianCalendar.getTime),
      "modified" -> new DateTime(node.getModifyDate.toGregorianCalendar.getTime),
      "type" -> node.getType,
      "container" -> JsBoolean(node.isIsContainer),
      "comment" -> node.getComment
    )
  }



}
