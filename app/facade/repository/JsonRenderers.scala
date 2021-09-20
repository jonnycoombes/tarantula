package facade.repository

import com.opentext.cws.docman.{AttributeGroup, BooleanValue, DataValue, DateValue, IntegerValue, Metadata, Node, NodeVersionInfo, RealValue, StringValue, Version}
import org.joda.time.DateTime
import play.api.libs.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, Json, Writes}
import play.api.libs.json.JodaWrites._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Json-related helpers for all things CWS.  Mainly [[Writes]] instances for the various different types returned as a result of CWS
 * outcalls
 */
object JsonRenderers {

  implicit lazy val dateWriter: Writes[DateTime] = jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss.SSSZ'")

  /**
   * Render a [[Node]] as a [[JsObject]]
   *
   * @param node the node to render
   * @return
   */
  def renderNodeToJson(node: Node): JsObject = {
    val rendition = Json.obj(
      "id" -> node.getID,
      "name" -> node.getName,
      "created" -> new DateTime(node.getCreateDate.toGregorianCalendar.getTime),
      "modified" -> new DateTime(node.getModifyDate.toGregorianCalendar.getTime),
      "type" -> node.getType,
      "container" -> JsBoolean(node.isIsContainer),
      "comment" -> node.getComment
    ) ++ Json.obj("meta" -> renderNodeMetaToJson(node.getMetadata))

    if (node.isIsVersionable) {
      rendition ++ Json.obj("version" -> renderVersionInfoToJson(node.getVersionInfo))
    }else{
      rendition
    }
  }

  def renderVersionInfoToJson(info: NodeVersionInfo): JsObject = {

    @inline def versionJson(version: Version): JsObject = {
      Json.obj(
        "number" -> version.getNumber,
        "created" ->  new DateTime(version.getCreateDate.toGregorianCalendar.getTime),
        "modified" -> new DateTime(version.getModifyDate.toGregorianCalendar.getTime),
        "filename" -> version.getFilename,
        "size" -> version.getFileDataSize
      )
    }

    @tailrec
    def versionArray(accum: JsArray, versions: List[Version]): JsArray = {
      versions match {
        case version :: tail => {
          versionArray(accum :+ versionJson(version), tail)
        }
        case Nil => accum
      }
    }
    Json.obj(
      "current" -> info.getVersionNum,
      "mimeType" -> info.getMimeType,
      "size" -> info.getFileDataSize.toInt,
      "number"-> info.getVersionNum,
      "all" -> versionArray(JsArray(Seq.empty), info.getVersions.asScala.toList)
    )
  }

  def renderNodeMetaToJson(metadata: Metadata) : JsArray = {

    val groups = metadata.getAttributeGroups.asScala.toList

    @inline def groupEntry(group : AttributeGroup) = {
      Json.obj(
        "category" -> group.getDisplayName,
        "values" -> dataValues(Json.obj(), group.getValues.asScala.toList)
      )
    }

    @tailrec
    def attributeGroups(accum : JsArray, groups : List[AttributeGroup]) : JsArray = {
      groups match {
        case Nil => accum
        case group :: tail => {
          attributeGroups(accum :+ groupEntry(group), tail)
        }
      }
    }

    @tailrec
    def dataValues(accum : JsObject, values : List[DataValue]) :  JsObject = {
      values match {
        case Nil => accum
        case value::tail => {
          dataValues(accum ++ Json.obj(value.getDescription -> convertValuesToJsonArray(value)), tail)
        }
      }
    }

    @inline def convertValuesToJsonArray(value : DataValue) : JsArray = {
      value match {
        case v : BooleanValue => {
          JsArray(v.getValues.asScala.toList.map(JsBoolean(_)))
        }
        case v: DateValue => {
          JsArray(v.getValues.asScala.toList.map(dt =>
            if (dt != null) {
              dateWriter.writes(new DateTime(dt.toGregorianCalendar.getTime))
            }else{
              JsString("")
            }))
        }
        case v: IntegerValue => {
          JsArray(v.getValues.asScala.toList.map(i => JsNumber(i.asInstanceOf[BigDecimal])))
        }
        case v : StringValue => {
          JsArray(v.getValues.asScala.toList.map(JsString))
        }
        case v : RealValue => {
          JsArray(v.getValues.asScala.toList.map(r => JsNumber(r.asInstanceOf[BigDecimal])))
        }
      }
    }

    attributeGroups(JsArray(Seq.empty), groups)
  }

}
