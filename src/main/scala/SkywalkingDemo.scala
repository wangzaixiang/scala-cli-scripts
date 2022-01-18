// using scala 2.13
// using lib com.lihaoyi::upickle:1.4.3
// using lib com.lihaoyi::requests:0.7.0
// using lib com.lihaoyi::mainargs:0.2.2
// using lib com.lihaoyi::os-lib:0.8.0

// using lib org.jxls:jxls:2.10.0
// using lib org.jxls:jxls-poi:2.10.0
// using resourceDir /Users/wangzaixiang/workspaces/wangzaixiang/staruml_dbutil/src/main/resources

// see https://scala-cli.virtuslab.org/install
// running scala-cli run SkywalkingDemo.scala -- exportTrace --traceId id

import mainargs._
import org.jxls.common.Context
import org.jxls.util.JxlsHelper
import os._
import ujson.Value

import java.io.{FileInputStream, FileOutputStream}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

object SkywalkingDemo {

  case class SpanKey(traceId: String, segmentId: String, spanId: Int)
  type Dict = Map[String, String]

  @main
  def exportTrace(traceId: String, output:String = "traceOut"): Unit = {

    val query =
      """
query queryTrace($traceId: ID!) {
  trace: queryTrace(traceId: $traceId) {
    spans {
      traceId
      segmentId
      spanId
      parentSpanId
      refs {
        traceId
        parentSegmentId
        parentSpanId
        type
      }
      serviceCode
      serviceInstanceName
      startTime
      endTime
      endpointName
      type
      peer
      component
      isError
      layer
      tags {
        key
        value
      }
      logs {
        time
        data {
          key
          value
        }
      }
    }
  }
  }
"""

    val reqStr = ujson.Obj("query" -> query, "variables" -> ujson.Obj(
//      "traceId" -> "5d555a1309d74bd5b5180190b45377c4.698.16423813493431873"
      "traceId" -> traceId
    ))

    val url = "https://ops-skywalking.kemai.cn/graphql"

    println(s"requests $url traceId = $traceId")
    val resp = requests.post(url, data = reqStr, readTimeout = 60*1000)

    val data = ujson.read(resp.data.toString)
    val spans = data("data")("trace")("spans").arr

    write.over( pwd/(output + ".json") , ujson.write(data, indent = 2))
    println("generate traceout.json completed")

    process(spans.toList, output)

  }

  @main
  def testJson(): Unit ={
    val source = read( pwd/ "demo1.json")
    val data = ujson.read(source)
    val spans = data("data")("trace")("spans").arr

    process(spans.toList, "traceOut.xlsx")
  }


  private def outputExcel(records: List[Map[String, String]], outputFile: String): Unit = {
    val spans: java.util.List[java.util.Map[String, String]] =
      records.map { elem => elem.asJava }.asJava
    val is = getClass.getResourceAsStream("spans.xlsx")
    val os = new FileOutputStream(outputFile + ".xlsx")
    val context = new Context()
    context.putVar("spans", spans)
    JxlsHelper.getInstance.processTemplate(is, os, context)
    os.close()
    is.close()

    println(s"generate ${outputFile} completed")
  }

  def process(spans: List[ujson.Value], output: String): Unit ={
    val spansByKey: Map[SpanKey, Value] = spans.map { span => (keyOf(span), span) }.toMap

    // all spans with parentSpanId == -1, which is the entry of a segment
    val entriesSpans: Seq[SpanKey] = spans.filter(span => span("parentSpanId").num == -1 )
      .map { span => keyOf(span) }

    // segment -> parent segment mapping
    val parents: Map[SpanKey, SpanKey] = entriesSpans.flatMap { key =>
      val span = spansByKey(key)

      if(span("refs").arr.size == 1) {
        val refTraceId = span("refs")(0)("traceId").str
        val refParentSegmentId = span("refs")(0)("parentSegmentId").str
        val refParentSpanId = span("refs")(0)("parentSpanId").num.toInt
        Some( key -> SpanKey(refTraceId, refParentSegmentId, refParentSpanId) )
      } else None

    }.toMap

    val root: SpanKey = entriesSpans.filterNot( parents contains _ )(0)

    val buffer = ListBuffer[Map[String,String]]()

    travel(0, root, spansByKey, parents, buffer)

    outputExcel(buffer.toList, output)

//    println(s"root span is ${ujson.write( spansByKey(root), indent = 2 )}")
  }

  def keyOf(span: ujson.Value): SpanKey =
    SpanKey( span("traceId").str, span("segmentId").str, span("spanId").num.toInt )

  def tag(span: ujson.Value, name: String): Option[String] = {
    val tags = span("tags").arr
    tags.filter( elem => elem("key").str == name).map { elem => elem("value").str }.headOption
  }

  def travel(indent: Int, root: SpanKey, spansByKey: Map[SpanKey, Value], parents: Map[SpanKey, SpanKey], buffer: ListBuffer[Dict]): Unit = {
    val spaces = "    " * indent
    val span = spansByKey(root)
    val time = span("endTime").num.toLong - span("startTime").num.toLong

    val sql  = if( span("endpointName").str == "Mysql/JDBI/PreparedStatement/execute") {
      tag(span, "db.statement").get
    }
    else if(span("component").str == "JdkHttp") {
      val tags = span("tags").arr
      val method = tag(span, "http.method").get
      val url = tag(span, "url").get
      s"$method $url"
    }
    else ""

    // println(s"${spaces}${span("serviceCode").str}\t${span("endpointName").str}\t(${time}ms)")
    val record = Map( "endpoint" -> (spaces + span("endpointName").str),
      "app" -> span("serviceCode").str,
      "time" -> s"${time}ms",
      "component" -> span("component").str,
      "peer" -> span("peer").str,
      "sql" -> sql
    )
    buffer.append(record)

    // children in same segment
    val childrenInSegment = spansByKey.values.filter { span =>
      span("traceId").str == root.traceId && span("segmentId").str == root.segmentId &&
        span("parentSpanId").num.toInt == root.spanId
    }.toList.sortBy( span => span("spanId").num.toInt)

    childrenInSegment.foreach { child =>
      travel(indent+1, keyOf(child), spansByKey, parents, buffer)
    }

    // child segment of this span
    parents.filter{ case (child, parent) => parent == root }.map { case (child, parent) => child }.toList match {
      case head :: tail =>
        travel(indent+1, head, spansByKey, parents, buffer)
      case Nil =>
    }

  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)

}