package facade.db

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import java.sql.PreparedStatement
import scala.collection.immutable.TreeMap
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.combinator.Parsers

object Search {

  /**
   * The basic tokens defined within the query grammar
   */
  object QueryTokens {

    sealed trait QueryToken

    sealed trait Comparison extends QueryToken

    sealed trait Logical extends QueryToken
    // Basic canonical tokens

    // Parentheses
    case object LP extends QueryToken

    case object RP extends QueryToken

    // Comparison operators
    case object Eq extends Comparison

    case object Neq extends Comparison

    case object Lt extends Comparison

    case object Lte extends Comparison

    case object Gt extends Comparison

    case object Gte extends Comparison

  }

  /**
   * Placeholder object for any case classes/traits utilised within the [[QueryParser]]
   */
  object QueryTerms {

    import QueryTokens.Comparison

    sealed trait Term

    sealed trait Identifier extends Term

    sealed trait Value extends Term

    // Identifiers
    case class AttributePath(components: List[String]) extends Identifier

    // Values - cover the available underlying CWS attribute types
    case class StringValue(value: String) extends Value

    case class IntegerValue(value: Int) extends Value

    case class DateValue(value: DateTime) extends Value

    case class BooleanValue(value: Boolean) extends Value

    /**
     * A query is recursively defined in terms of a [[Clause]] tree
     */
    abstract class Clause

    /**
     * A predicate consists of an [[Identifier]], a [[Comparison]] operator and a value
     *
     * @param id         the [[Identifier]] which will either be of the form "node.core_attribute" or "category.attribute"
     * @param comparison the comparison operator to use: [[QueryTokens.Eq]], [[QueryTokens.Gt]] etc...
     * @param value      the value to be used in the comparison
     */
    case class Predicate(id: Identifier, comparison: Comparison, value: Value) extends Clause

    /**
     * A conjunction
     *
     * @param left
     * @param right
     */
    case class AndClause(left: Clause, right: Clause) extends Clause

    /**
     * A disjunction
     *
     * @param left
     * @param right
     */
    case class OrClause(left: Clause, right: Clause) extends Clause
  }

  /**
   * A parser for simple the simple query language supported by the Facade.  If a query is successfully parsed, then an AST is produced
   * that is suitable for recursive evaluation/reduction.
   */
  object QueryParser extends RegexParsers {

    import QueryTerms._
    import QueryTokens._

    val formatter = DateTimeFormat.forPattern("dd/MM/yyyy")

    // Base rules for canonical tokens
    def lparen = "(" ^^ { _ ⇒ LP }

    def rparen = ")" ^^ { _ ⇒ RP }

    def eq = "==" ^^ { _ ⇒ Eq }

    def neq = "!=" ^^ { _ ⇒ Neq }

    def lt = "<" ^^ { _ ⇒ Lt }

    def lte = "<=" ^^ { _ ⇒ Lte }

    def gt = ">" ^^ { _ ⇒ Gt }

    def gte = ">=" ^^ { _ ⇒ Gte }

    // Different idenfifier types
    def path = """(\w|\s)+\.(\w|\s)+""".r ^^ { str ⇒ AttributePath(str.replaceAll("""\s+$""", "").split('.').toList) }

    def int = """-?\d+""".r ^^ { str ⇒ IntegerValue(str.toInt) }

    def string = """\'(\w|\/|\W|\s)+\'""".r ^^ { str ⇒ StringValue(str) }

    def bool = ("true" | "false") ^^ { case "true" ⇒ BooleanValue(true); case "false" ⇒ BooleanValue(false) }

    /**
     * Returns a parser instance that understands how to extract a [[Value]] instance from the supplied input.  String values are treated
     * specially, given that [[DateTime]] instances are encoded as string values of a given format.  (See [[formatter]]).
     *
     * @return
     */
    def value: Parser[Value] = (int | bool | string) ^^ {
      case s: StringValue ⇒
        try {
          val dt = formatter.parseDateTime(s.value.replaceAll("\'", ""))
          DateValue(dt)
        }
        catch {
          case _: Throwable ⇒ StringValue(s.value.replaceAll("\'", ""))
        }
      case i: IntegerValue ⇒ i
      case b: BooleanValue ⇒ b
      case d: DateValue ⇒ d
    }

    /**
     * Returns a parser that understands how to parse a [[Comparison]] operator from the supplied input.
     *
     * @return
     */
    def comparison: Parser[Comparison] = eq | neq | lte | lt | gte | gt

    /**
     * Returns a parser that understands how to parse a single [[Predicate]] from the supplied input.  Essentially, this parser is a
     * combinator over the [[path]], [[comparison]] and [[value]] parsers.  A [[Predicate]] is the atomic unit of evaluation for a given
     * query.
     *
     * @return
     */
    def predicate: Parser[Clause] = path ~ comparison ~ value ^^ {
      case p ~ c ~ v ⇒ Predicate(p, c, v)
    }

    /**
     * Returns a parser for a complete query.  Essentially determines how multiple [[Predicate]] instances may be combined together using
     * logical operators and parentheses in order to structure a query.
     *
     * @return
     */
    def query: Parser[Clause] = (predicate | parens) * (
      "&&" ^^^ { (c1: Clause, c2: Clause) ⇒ AndClause(c1, c2) } |
        "||" ^^^ { (c1: Clause, c2: Clause) ⇒ OrClause(c1, c2) }
      )

    /**
     * Returns a parser for a [[Clause]] which may be surrounded within a pair of parentheses.
     *
     * @return
     */
    def parens: Parser[Clause] = lparen ~> query <~ rparen

    /**
     * Just apply the [[query]] parser to a string input
     *
     * @param input The query to be parsed
     * @return A [[Success]] or [[NoSuccess]] depending on whether the query is valid or not
     */
    def apply(input: String) = parseAll(query, input)

    /**
     * Utility function for testing the recursive evaluation of the generated AST.
     *
     * @param query
     * @param level
     */
    def prettyPrint(query: Clause, level: Int): Unit = {
      query match {
        case OrClause(l, r) =>
          print(s"${identation(level)} Or\n")
          prettyPrint(l, level + 1)
          prettyPrint(r, level + 1)
        case AndClause(l, r) =>
          print(s"${identation(level)} And\n")
          prettyPrint(l, level + 1)
          prettyPrint(r, level + 1)
        case Predicate(p, c, v) =>
          print(s"${identation(level)} ($p:$c:$v)\n")
      }

    }

    @inline private def identation(level: Int) = {
      var tabs = List[String]()
      for (_ ← 0 to level) {
        tabs = "\t" :: tabs
      }
      tabs.mkString
    }

    def prettyPrintToString(query: Clause, level: Int): String = {

      var output = ""
      query match {
        case OrClause(l, r) =>
          output += s"${identation(level)} Or\n"
          output += prettyPrintToString(l, level + 1)
          output += prettyPrintToString(r, level + 1)
        case AndClause(l, r) =>
          output += s"${identation(level)} And\n"
          output += prettyPrintToString(l, level + 1)
          output += prettyPrintToString(r, level + 1)
        case Predicate(p, c, v) =>
          output += s"${identation(level)} ($p:$c:$v)\n"
      }

      output

    }
  }

  /**
   * Object containing methods for translating a [[Parsers.ParseResult[Clause]] instance into a combined SQL statement which may be
   * executed and the results parsed in order to create a result set
   */
  object QueryInterpreter {

    import QueryTerms._
    import QueryParser._

    /**
     * A lower case comparison [[Ordering]] used to lookup keys within the core fields map
     */
    private object LowerCaseOrdering extends Ordering[String] {
      def compare(key1: String, key2: String) = key2.toLowerCase.compareTo(key1.toLowerCase)
    }

    /**
     * A static map containing a mapping between query language node core property names and the corresponding fields/columns within
     * DTreeCore
     */
    private lazy val DTreeCoreFieldMappings: TreeMap[String, String] = TreeMap(
      "id" -> "DataID",
      "dataId" -> "DataID",
      "parentId" -> "ParentID",
      "name" -> "Name",
      "ownerId" -> "OwnerID",
      "createDate" -> "CreateDate",
      "modifyDate" -> "ModifyDate",
      "subType" -> "SubType"
    )(LowerCaseOrdering)

    private def attributePredicateToSql(predicate: Predicate, schemaPrefix: String): Option[String] = {
      val path = predicate.id.asInstanceOf[AttributePath]
      val category = path.components(0)
      val attribute = path.components(1)
      attributePredicateWhereClause(predicate) match {
        case Some(where) =>
          val sql =
            s"""
               |      select a.DataID
               |      	from $schemaPrefix.DTreeCore a inner join $schemaPrefix.LLAttrData b on a.DataID = b.ID
               |          and a.VersionNum = b.VerNum
               |      	inner join
               |      		( select CategoryId, CategoryVersion, AttributeIndex
               |      			from $schemaPrefix.Facade_Attributes
               |      				where Attribute = '$attribute'
               |      					and Category = '$category') sq
               |      			on b.DefID = sq.CategoryId
               |      	and b.DefVerN = sq.CategoryVersion
               |      	and b.AttrID = sq.AttributeIndex
               |       $where and a.Deleted = 0
               |       """.stripMargin

          Some(sql)
        case _ =>
          None
      }
    }


    def attributePredicateWhereClause(predicate: Predicate): Option[String] = {

      def insertTypedColumn(value: QueryTerms.Value) = {
        value match {
          case StringValue(_) => "b.ValStr"
          case IntegerValue(_) => "b.ValInt"
          case DateValue(_) => "b.ValDate"
          case BooleanValue(_) => "b.ValInt"
          case _ => "b.ValStr"
        }
      }

      predicate.comparison match {
        case QueryTokens.Gt =>
          Some(s"where ${insertTypedColumn(predicate.value)} > (?)")
        case QueryTokens.Gte =>
          Some(s"where ${insertTypedColumn(predicate.value)} >= (?)")
        case QueryTokens.Lt =>
          Some(s"where ${insertTypedColumn(predicate.value)} < (?)")
        case QueryTokens.Lte =>
          Some(s"where ${insertTypedColumn(predicate.value)} <= (?)")
        case QueryTokens.Neq =>
          Some(s"where ${insertTypedColumn(predicate.value)} <> (?)")
        case QueryTokens.Eq =>
          Some(s"where ${insertTypedColumn(predicate.value)} = (?)")
        case _ => None
      }
    }

    /**
     *
     * @param stmt
     * @param predicate
     * @param index
     */
    def addParameterValueFromPredicate(stmt: PreparedStatement, predicate: Predicate, index: Int) = {
      predicate.value match {
        case StringValue(value) => stmt.setString(index, value)
        case IntegerValue(value) => stmt.setInt(index, value)
        case DateValue(value) => stmt.setDate(index, new java.sql.Date(value.toDate.getTime))
        case BooleanValue(value) => {
          value match {
            case true => stmt.setInt(index, 1)
            case false => stmt.setInt(index, 0)
          }
        }
      }
    }

    def corePredicateToSql(predicate: Predicate, schemaPrefix: String): Option[String] = {
      corePredicateWhereClause(predicate) match {
        case Some(where) =>
          val sql =
            s"""
               |select a.DataID
               |  from $schemaPrefix.DTreeCore a
               |   $where and a.Deleted = 0
               |""".stripMargin
          Some(sql)
        case None =>
          None
      }
    }

    def corePredicateWhereClause(predicate: Predicate): Option[String] = {

      val column = predicate.id.asInstanceOf[AttributePath].components(1)
      DTreeCoreFieldMappings.contains(column) match {
        case true =>
          predicate.comparison match {
            case QueryTokens.Gt =>
              Some(s"where ${DTreeCoreFieldMappings(column)} > (?)")
            case QueryTokens.Gte =>
              Some(s"where ${DTreeCoreFieldMappings(column)} >= (?)")
            case QueryTokens.Lt =>
              Some(s"where ${DTreeCoreFieldMappings(column)}< (?)")
            case QueryTokens.Lte =>
              Some(s"where ${DTreeCoreFieldMappings(column)} <= (?)")
            case QueryTokens.Neq =>
              Some(s"where ${DTreeCoreFieldMappings(column)} <> (?)")
            case _ =>
              Some(s"where ${DTreeCoreFieldMappings(column)} = (?)")
          }
        case _ => None
      }
    }

  }

}