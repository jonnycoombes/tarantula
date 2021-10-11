package facade.db

import anorm.{NamedParameter, SQL}
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.immutable.TreeMap
import scala.util.parsing.combinator.{Parsers, RegexParsers}

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
    case class Predicate(id: Identifier, comparison: Comparison, value: Value) extends Clause {
      self =>
      /**
       * Compute a unique parameter name for the predicate, which can be used in order to build any generated SQL statements
       *
       * @return
       */
      def deriveBindingTag(): String = {
        s"p${Math.abs(self.hashCode())}"
      }
    }

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

    val formatter: DateTimeFormatter = DateTimeFormat.forPattern("dd/MM/yyyy")

    // Base rules for canonical tokens
    private def lparen = "(" ^^ { _ => LP }

    private def rparen = ")" ^^ { _ => RP }

    private def eq = "==" ^^ { _ => Eq }

    private def neq = "!=" ^^ { _ => Neq }

    private def lt = "<" ^^ { _ => Lt }

    private def lte = "<=" ^^ { _ => Lte }

    private def gt = ">" ^^ { _ => Gt }

    private def gte = ">=" ^^ { _ => Gte }

    // Different idenfifier types
    private def path = """(\w|\s)+\.(\w|\s)+""".r ^^ { str => AttributePath(str.replaceAll("""\s+$""", "").split('.').toList) }

    private def int = """-?\d+""".r ^^ { str => IntegerValue(str.toInt) }

    private def string = """\'(\w|\/|\W|\s)+\'""".r ^^ { str => StringValue(str) }

    private def bool = ("true" | "false") ^^ { case "true" => BooleanValue(true); case "false" => BooleanValue(false) }

    /**
     * Returns a parser instance that understands how to extract a [[Value]] instance from the supplied input.  String values are treated
     * specially, given that [[DateTime]] instances are encoded as string values of a given format.  (See [[formatter]]).
     *
     * @return
     */
    private def value: Parser[Value] = (int | bool | string) ^^ {
      case s: StringValue => try {
        val dt = formatter.parseDateTime(s.value.replaceAll("\'", ""))
        DateValue(dt)
      }
      catch {
        case _: Throwable => StringValue(s.value.replaceAll("\'", ""))
      }
      case i: IntegerValue => i
      case b: BooleanValue => b
      case d: DateValue => d
    }

    /**
     * Returns a parser that understands how to parse a [[Comparison]] operator from the supplied input.
     *
     * @return
     */
    private def comparison: Parser[Comparison] = eq | neq | lte | lt | gte | gt

    /**
     * Returns a parser that understands how to parse a single [[Predicate]] from the supplied input.  Essentially, this parser is a
     * combinator over the [[path]], [[comparison]] and [[value]] parsers.  A [[Predicate]] is the atomic unit of evaluation for a given
     * query.
     *
     * @return
     */
    private def predicate: Parser[Clause] = path ~ comparison ~ value ^^ {
      case p ~ c ~ v => Predicate(p, c, v)
    }

    /**
     * Returns a parser for a complete query.  Essentially determines how multiple [[Predicate]] instances may be combined together using
     * logical operators and parentheses in order to structure a query.
     *
     * @return
     */
    private def query: Parser[Clause] = (predicate | parens) * (
      "&&" ^^^ { (c1: Clause, c2: Clause) => AndClause(c1, c2) } |
        "||" ^^^ { (c1: Clause, c2: Clause) => OrClause(c1, c2) }
      )

    /**
     * Returns a parser for a [[Clause]] which may be surrounded within a pair of parentheses.
     *
     * @return
     */
    private def parens: Parser[Clause] = lparen ~> query <~ rparen

    /**
     * Just apply the [[query]] parser to a string input
     *
     * @param input The query to be parsed
     * @return A [[Success]] or [[NoSuccess]] depending on whether the query is valid or not
     */
    def apply(input: String): QueryParser.ParseResult[Clause] = parseAll(query, input)

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

    /**
     * Utility function for padding out an indentation level
     * @param level
     * @return
     */
    @inline private def identation(level: Int) = {
      var tabs = List[String]()
      for (_ <- 0 to level) {
        tabs = "\t" :: tabs
      }
      tabs.mkString
    }

    /**
     * Pretty prints a parsed query to a string
     * @param query the [[Clause]] to print
     * @param level the level of indentation to apply
     * @return
     */
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

    /**
     * A lower case comparison [[Ordering]] used to lookup keys within the core fields map
     */
    private object LowerCaseOrdering extends Ordering[String] {
      def compare(key1: String, key2: String): Int = key2.toLowerCase.compareTo(key1.toLowerCase)
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

    /**
     * Takes a [[Predicate]] which involves a category.attribute pair and generates the equivalent SQL used to satisfy the predicate
     * within the database
     * @param predicate the source [[Predicate]] - should be a predicate with an id in the form category.attribute
     * @param schemaPrefix the schema prefix to apply to the generated SQL
     * @return
     */
    private def attributePredicateToSql(predicate: Predicate, schemaPrefix: String): Option[String] = {
      val path = predicate.id.asInstanceOf[AttributePath]
      val category = path.components.head
      val attribute = path.components(1)
      attributePredicateWhereClause(predicate) match {
        case Some(where) =>
          val sql =
            s"""
               |      select a.ParentID, a.DataID, a.VersionNum, a.Name, a.SubType, a.OriginDataID, a.CreateDate, a.ModifyDate
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

    /**
     * Maps the where clause within a predicate SQL statement to the correct column within the underlying SQL view structure
     * @param predicate the [[Predicate]]
     * @return
     */
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
          Some(s"where ${insertTypedColumn(predicate.value)} > {${predicate.deriveBindingTag()}}")
        case QueryTokens.Gte =>
          Some(s"where ${insertTypedColumn(predicate.value)} >= {${predicate.deriveBindingTag()}}")
        case QueryTokens.Lt =>
          Some(s"where ${insertTypedColumn(predicate.value)} < {${predicate.deriveBindingTag()}} ")
        case QueryTokens.Lte =>
          Some(s"where ${insertTypedColumn(predicate.value)} <={${predicate.deriveBindingTag()}} ")
        case QueryTokens.Neq =>
          Some(s"where ${insertTypedColumn(predicate.value)} <> {${predicate.deriveBindingTag()}}")
        case QueryTokens.Eq =>
          Some(s"where ${insertTypedColumn(predicate.value)} = {${predicate.deriveBindingTag()}} ")
        case _ => None
      }
    }


    /**
     *
     * @param predicate
     * @param schemaPrefix
     * @return
     */
    def corePredicateToSql(predicate: Predicate, schemaPrefix: String): Option[String] = {
      corePredicateWhereClause(predicate) match {
        case Some(where) =>
          val sql =
            s"""
               |select a.ParentID, a.DataID, a.VersionNum, a.Name, a.SubType, a.OriginDataID, a.CreateDate, a.ModifyDate
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
      if (DTreeCoreFieldMappings.contains(column)) {
        predicate.comparison match {
          case QueryTokens.Gt =>
            Some(s"where ${DTreeCoreFieldMappings(column)} > {${predicate.deriveBindingTag()}}")
          case QueryTokens.Gte =>
            Some(s"where ${DTreeCoreFieldMappings(column)} >= {${predicate.deriveBindingTag()}} ")
          case QueryTokens.Lt =>
            Some(s"where ${DTreeCoreFieldMappings(column)}< {${predicate.deriveBindingTag()}} ")
          case QueryTokens.Lte =>
            Some(s"where ${DTreeCoreFieldMappings(column)} <= {${predicate.deriveBindingTag()}} ")
          case QueryTokens.Neq =>
            Some(s"where ${DTreeCoreFieldMappings(column)} <> {${predicate.deriveBindingTag()}}  ")
          case _ =>
            Some(s"where ${DTreeCoreFieldMappings(column)} = {${predicate.deriveBindingTag()}} ")
        }
      } else {
        None
      }
    }

    @inline private def translatePredicateToSql(predicate: Predicate, schemaPrefix : String): String = {
      predicate.id.asInstanceOf[AttributePath].components.head match {
        case "node" => corePredicateToSql(predicate, schemaPrefix).getOrElse("")
        case _ => attributePredicateToSql(predicate, schemaPrefix).getOrElse("")
      }
    }

    def translateClauseToSql(clause: Clause, schemaPrefix: String): String = {
      var output = ""
      clause match {
        case OrClause(l, r) => output += translateClauseToSql(l, schemaPrefix)
          output += " union "
          output += translateClauseToSql(r, schemaPrefix)
        case AndClause(l, r) => output += translateClauseToSql(l, schemaPrefix)
          output += " intersect "
          output += translateClauseToSql(r, schemaPrefix)
        case predicate@Predicate(_, _, _) => output += translatePredicateToSql(predicate, schemaPrefix)
      }
      output
    }

    def bindClauseToSql(rawSql: String, clause: Clause) = {
      val predicates = flattenPredicates(List.empty, clause)
      val params = predicates.map(bindPredicate(_))
      SQL(rawSql).on(params: _*)
    }

    def bindPredicate(predicate: Predicate): NamedParameter = {
      predicate.value match {
        case StringValue(s) => NamedParameter(predicate.deriveBindingTag(), s)
        case IntegerValue(i) => NamedParameter(predicate.deriveBindingTag(), i)
        case DateValue(d) => NamedParameter(predicate.deriveBindingTag(), d.toDate)
        case BooleanValue(b) =>
          if (b) {
            NamedParameter(predicate.deriveBindingTag(), 1)
          } else {
            NamedParameter(predicate.deriveBindingTag(), 0)
          }
      }
    }

    def flattenPredicates(accum: List[Predicate], clause: Clause): List[Predicate] = {
      clause match {
        case OrClause(l, r) =>
          flattenPredicates(accum, l) ++ flattenPredicates(accum, r)
        case AndClause(l, r) =>
          flattenPredicates(accum, l) ++ flattenPredicates(accum, r)
        case predicate@Predicate(_, _, _) =>
          predicate :: accum
      }
    }

  }


}
