package ja.com
import java.util.regex.Matcher

import ja.com.Common.cdxItem
import ja.conf.JobSparkConf

import scala.collection.mutable.ListBuffer

// import ja.conf._
/**
  * Created by Ja on 21/05/2017.
  */

object Job1 {
  case class t(x : Long, y: String)
  case class allLines( linenumber: Long  , isvalidheader:Boolean ,isheader:Boolean, url:String, urltime:String, mime:String , line:String)
  import java.util.regex.Pattern
  val dataLinePattern = Pattern.compile("(\\d{14})")

  def isCurrentLineIsValidHeader(line:String, lstMimes: List[String] ) : Boolean  = {

      if(lstMimes.exists(line.contains)) {
        val matcher: Matcher = dataLinePattern.matcher(line)
        matcher.find

    }
    else {
      false
    }
  }
  def isCurrentLineIsHeader(line:String) : Boolean  = {
      val valid1 = (line.split(" ").length > 2)
      val matcher: Matcher = dataLinePattern.matcher(line)
      matcher.find && valid1

  }

  def main(args: Array[String]): Unit = {

    val s = "com,google)/ 20090831083204 http://www.google.com/ text/html 302 MRIC3B6QBCMMI7MLO76A2P5ONACU4L7H - - 335 26267 EA-TNA-0709-biglotteryfund.org.uk-p-20090831083143-00000.arc.gz"
    val sp = s.split(" ")
    sp.foreach(println)
    val txtRDD = JobSparkConf.sc.textFile("C:\\Users\\Ja\\Google Drive\\srcfile\\Note\\srcfile\\EA-TNA-0709-biglotteryfund.org.uk-p-20090831083143-00000.cdx")
    val rddLines = txtRDD.mapPartitionsWithIndex { (idx, iter) => if (idx == 0) iter.drop(1) else iter }
    import JobSparkConf.sqlContext.implicits._
    val cdxItems = rddLines.map(x => cdxItem(x.split(" ")(0)
      ,x.split(" ")(1),x.split(" ")(2), x.split(" ")(3),
      x.split(" ")(4),x.split(" ")(5), x.split(" ")(6),
       x.split(" ")(7),x.split(" ")(8),x.split(" ")(9))
    ).toDF()
    // robots.txt, .js and, .json extensions, and any files which returned an HTTP status code of 400
    val filteredCDXitems = cdxItems
      .filter(!(cdxItems("a_origina_url").endsWith("robots.txt")
        || cdxItems("a_origina_url").endsWith(".js")
        || cdxItems("a_origina_url").endsWith(".json")
        ))

// s_response_code from 400  to 511
    import ja.com.Common._


    val filteredCDXitems1 = filteredCDXitems
      .filter(!(filteredCDXitems("s_response_code").between("400", "511")))
       .withColumn("New_URL", toGetURLnew(filteredCDXitems("a_origina_url")))

    // toGetURL(txtRDD).toString

    println(filteredCDXitems1)
    filteredCDXitems1.show(10000,truncate = false)
    filteredCDXitems1.registerTempTable("mainT")

    val inscopeDomains = JobSparkConf.sc.textFile("C:\\Users\\Ja\\Google Drive\\oldfiles\\Note\\srcfile\\SearchWhitelist\\dns_*")
    val domainsDf =   inscopeDomains.map( x => {
              if(x.indexOf("/") >= 0 )
                x.substring(0,x.indexOf("/")).replaceAll("\"", "")
              else
                x.replaceAll("\"", "")
    }).toDF("domain")


    println("count 1=" + domainsDf.count())
    domainsDf.registerTempTable("domainT")

    domainsDf.show(10)
    val joinedDF = JobSparkConf.sqlContext.sql("select mainT.* from mainT inner join domainT" +
      " ON mainT.New_URL LIKE concat('%',domainT.domain,'%') ")
    joinedDF.registerTempTable("joinedTable")
    joinedDF.show(100)

    /// Arc file starts here


    val txtArcRDD = JobSparkConf.sc.textFile("C:\\Users\\Ja\\Google Drive\\srcfile\\Note\\srcfile\\EA-TNA-0709-biglotteryfund.org.uk-p-20090831083143-00000.arc")
    val indexedArcRDD = txtArcRDD.zipWithIndex
    import JobSparkConf.sqlContext.implicits._
    val mainDF = indexedArcRDD.toDF("line", "linenumber")
    mainDF.registerTempTable("tmp1")
    val query  = "Select tmp1.linenumber + 1 linenumber, tmp1.line from tmp1 " +
      " join (select linenumber from tmp1 where trim(line) = '') as t2 " +
      " on tmp1.linenumber = t2.linenumber + 1 where trim(tmp1.line) <> '' "
      //" left join (select distinct m_mime_type_of_original_document mime from joinedTable) as t3 "
      //" on tmp1.line like concat('%',t3.mime,'%') where trim(tmp1.line) <> '' "

    val dfMimes = joinedDF.select(joinedDF("m_mime_type_of_original_document")).distinct().toDF("mime")
  //  dfMimes.registerTempTable("mimestable")
    dfMimes.show(100)
    //dfMimes.count()   // TODO : 1
    //val broadcastVar = sc.broadcast(Array(1, 2, 3))
    var lstMimes = new ListBuffer[String]()
    val mlist1 = dfMimes.collect()
    println("fk")
    val mlist = mlist1.foreach(x => {
      println(x)
      lstMimes += x.getAs[String]("mime")
    })
    val broadcastVar = JobSparkConf.sc.broadcast(lstMimes.toList)

    val f1= JobSparkConf.sqlContext.sql(query).select("linenumber", "line").rdd

    val linenumbersBtwSpaces = f1.collect().map( x => x.getAs[Long]("linenumber"))
     println("line number for space lines")
    linenumbersBtwSpaces.sorted.take(100).foreach(println)

    println("fk2")
    import java.util.regex.Pattern
    val pattern = Pattern.compile("(\\d{14})")

    val f4 = mainDF.map(x => {
      val ln = x.getAs[Long]("linenumber")
      val line = x.getAs[String]("line")
      var isvalidheader = isCurrentLineIsValidHeader(line, lstMimes.toList)
      var isheader = isCurrentLineIsHeader(line)
    var urltime = ""
      var url = ""
      var mimetype = ""
      if(isvalidheader){
          val arr = line.split(" ")
        url = arr(0)
        urltime = arr(2)
        mimetype = arr(3)
      }

      allLines( ln+1  , isvalidheader ,isheader,url,urltime,mimetype,  line)

    }).toDF().registerTempTable("fk4")
    println("key - values")
    val arcDF = JobSparkConf.sqlContext.sql("select * from fk4 order by linenumber")
    arcDF.show(1000)
    //joinedTable
    val finalDF1 = JobSparkConf.sqlContext.sql("select distinct fk4.* from fk4 " +
      " join joinedTable on trim(joinedTable.b_date) = trim(fk4.urltime)  " +
      " and joinedTable.m_mime_type_of_original_document = trim(fk4.mime) " +
      " and fk4.url like concat('%',joinedTable.New_URL,'%')  " +
      "order by fk4.linenumber").toDF()


    finalDF1.registerTempTable("cdxJoinArcTable")

    finalDF1.printSchema()

    finalDF1.show(100, truncate = false)
  }
}
