// using scala 2.13
// using lib com.github.wangzaixiang::scala-sql:2.0.7
// using lib com.lihaoyi::mainargs:0.2.2
// using lib com.lihaoyi::os-lib:0.8.0
// using lib mysql:mysql-connector-java:8.0.27
// using lib com.lihaoyi::ujson:1.4.4

import mainargs.{ParserForMethods, main}
import os.Path
import wangzx.scala_commons.sql._

import java.sql.{Connection, DatabaseMetaData, DriverManager, ResultSet}

object staruml_dbgen {

  implicit class ResultSetEx(rs: ResultSet) {
    def foreach(op: ResultSet => Unit): Unit = {
      while (rs.next()) {
        op(rs)
      }
    }
  }

  val REG = """.*_(\d+)""".r


  @main
  def exportUML(driver: String, url: String, user: String, password: String, catalog: String, outFile: String): Unit = {

    Class.forName(driver)

    val connection = DriverManager.getConnection(url, user, password)
    val dbmeta: DatabaseMetaData = connection.getMetaData

    val tables = dbmeta.getTables(catalog, "%", "%", null)

    val entities = collection.mutable.ArrayBuffer[ujson.Value]()
    tables.foreach { rs =>
      rs.getString("TABLE_NAME") match {
        case REG(num) if num.toInt >= 1 => // 分表，只保留 _0 的表信息
        case _ =>
          val comment = getTableComment(connection, rs.getString("TABLE_CAT"), rs.getString("TABLE_NAME"))
          // println(s"[${rs.getString("TABLE_CAT")}]-[${rs.getString("TABLE_SCHEM")}]-[${rs.getString("TABLE_NAME")}]")
          val columns = dbmeta.getColumns(rs.getString("TABLE_CAT"), rs.getString("TABLE_SCHEM"),
            rs.getString("TABLE_NAME"), "%")
          val primaryKey = {
            val rs2 = dbmeta.getPrimaryKeys(rs.getString("TABLE_CAT"), rs.getString("TABLE_SCHEM"),
              rs.getString("TABLE_NAME"))
            val names = collection.mutable.ListBuffer[String]()
            rs2.foreach { rs => names.append(rs.getString("COLUMN_NAME")) }
            names.toList
          }
          val entity = createEntity(rs, columns, primaryKey)
          entity.value("documentation") = comment // TODO
          entities.append(entity)
      }
    }

    val model = ujson.Obj("_type" -> "ERDDataModel",
      "name" -> url,
      "ownedElements" -> new ujson.Arr(entities)
    )

    val content = ujson.write(model, indent = 2, escapeUnicode = false)

    os.write.over(Path(outFile), content)
    println(s"write $outFile completed")
  }

  private def getTableComment(connection: Connection, catalog: String, table: String): String = {
    connection.rows[Row](sql"SELECT * FROM information_schema.tables WHERE table_schema = ${catalog} and table_name= ${table}") match {
      case List(row) => row.getString("table_comment")
      case _ => null
    }
  }

  private def createEntity(tableRS: ResultSet, columns: ResultSet, primaryKeyColumns: List[String]): ujson.Obj = {
    val obj = ujson.Obj("_type" -> "ERDEntity", "name" -> tableRS.getString("TABLE_NAME"),
      "documentation" -> tableRS.getString("REMARKS"))
    val cols = ujson.Arr()
    columns.foreach { colRS =>
      val column = ujson.Obj(
        "_type" -> "ERDColumn",
        "name" -> colRS.getString("COLUMN_NAME"),
        "type" -> colRS.getString("TYPE_NAME"),
        "length" -> colRS.getString("COLUMN_SIZE"),
        "primaryKey" -> primaryKeyColumns.contains(colRS.getString("COLUMN_NAME")),
        "documentation" -> colRS.getString("REMARKS")
      )
      cols.value.append(column)
    }
    obj.value("columns") = cols
    obj
  }


  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)

}
