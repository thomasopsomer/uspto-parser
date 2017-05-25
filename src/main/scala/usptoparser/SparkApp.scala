package usptoparser

/**
  * Created by thomasopsomer on 23/05/2017.
  */


import java.io.File
import org.apache.spark.{Accumulable, SparkConf, SparkContext}
import org.apache.spark.sql.{SQLContext, SaveMode}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.model.GetObjectRequest
import org.apache.spark.rdd.RDD
import scala.collection.mutable
import scala.util.Try
import scopt.OptionParser


object SparkApp {

  case class Params(
                     folderPath: String = null,
                     outputPath: String = null,
                     numPartitions: Option[Int] = None,
                     test: Boolean = false
                   )

  def parsePartitionsS3(partition: Iterator[(String, String)],
                        tmpZipFolder: String = "/tmp/uspto-zip/"): Iterator[(String, Option[PatentDocument])] = {
    // init s3 transfert manager
    val s3client = new AmazonS3Client()

    // create tar and pdf folder
    val zipDir = new File(tmpZipFolder)
    if (!zipDir.exists) zipDir.mkdirs

    val myIterator: Iterator[(String, Option[PatentDocument])] = partition.flatMap(x => {
      //Get file form S3
      val bucket: String = x._1
      val key: String = x._2
      val fname: String = tmpZipFolder + key.split("/").last
      val resFile: File = new File(fname)
      s3client.getObject(new GetObjectRequest(bucket, key), resFile)
      // init parser wrapper
      val usptoWrapper = new UsptoParserWrapper(resFile)
      // parse data
      try {
        val patentsIt = usptoWrapper.parseZipFileIt()
        patentsIt.map(x => (fname, x))
      }
      finally {
        resFile.delete()
      }
    })
    // return iterator for mapPartition
    myIterator
  }

  def parsePartitionsLocal(partition: Iterator[String],
                           tmpZipFolder: String = "/tmp/uspto-zip/",
                           delete: Boolean = false): Iterator[(String, Option[PatentDocument])] = {

    val myIterator: Iterator[(String, Option[PatentDocument])] = partition.flatMap(x => {
      val file = new File(x)
      // init parser wrapper
      val usptoWrapper = new UsptoParserWrapper(file)

      try {
        // val patentsIt = usptoWrapper.parseZipFile()
        val patentsIt = usptoWrapper.parseZipFileIt()
        patentsIt.map(x => (file.getName, x))
      }
      finally {
        if (delete) file.delete()
      }
    })
    // return iterator for mapPartition
    myIterator
  }

  def run(params: Params) = {

    val conf = new SparkConf()
      .setAppName("GrobidSpark")
      .set("spark.driver.allowMultipleContexts", "true")

    val sc = SparkContext.getOrCreate(conf)
    sc.setLogLevel("ERROR")
    sc.hadoopConfiguration.set("fs.s3a.connection.timeout", "500000")

    // sqlContext and implicits for dataframe
    val hc = new SQLContext(sc)
    import hc.implicits._

    var zipPathRDD: RDD[String] = sc.emptyRDD[String]
    var patentParsedRDD: RDD[PatentDocument] = sc.emptyRDD[PatentDocument]

    // Accumulator for errors
    val acc: Accumulable[mutable.MutableList[String], String] =
      sc.accumulableCollection(initialValue = mutable.MutableList())

    if (usptoparser.Helper.isS3(params.folderPath)) {
      // for aws manipulation replace s3a by s3
      val folderPath = params.folderPath.replaceAll("s3a://", "s3://")

      // get list of file to process from the s3folder and make it an RDD
      val s3: AmazonS3Client = new AmazonS3Client()
      val region_name = sys.env.getOrElse("AWS_DEFAULT_REGION", "EU_WEST_1")
      val region = Region.getRegion(Regions.valueOf(region_name))
      s3.setRegion(region)

      // look for logs not to parse already parsed archive
      val archive_logs: Array[String] = Try({
        sc.textFile(params.outputPath + "/log*/*")
          .map(Helper.splitBucketKey).map(_._2).collect()
      }).getOrElse(Array.empty[String])

      // get list of s3 keys, and filter them
      var bucketKeysList: List[(String, String)] = Helper.listS3FolderKeys(s3, folderPath)
        .filter(x => x._2.endsWith("zip"))
        .filter(x => !archive_logs.contains(x._2))
      bucketKeysList = if (params.test) bucketKeysList.slice(0, 4) else bucketKeysList

      if (bucketKeysList.nonEmpty) {
        println(f"Found ${bucketKeysList.size} archives to process.")
        // handle number of partitions
        val numPartitions = params.numPartitions.getOrElse(bucketKeysList.size / 3)

        val zipBucketKeyRDD: RDD[(String, String)] = sc.parallelize(bucketKeysList, numPartitions)

        patentParsedRDD = zipBucketKeyRDD
          .mapPartitions(part => parsePartitionsS3(part))
          .filter( _._2 match {
            case Some(p) => true
            case None => false;
          })
          .map(_._2.get)

        // just for log
        zipPathRDD = zipBucketKeyRDD.map(x => f"s3://${x._1}/${x._2}")
      }
      else {
        println("No new archive to parse")
        sys.exit(1)
      }
    }
    else {
      val zipFilesPath: List[String] = Helper.getRecursiveListOfFilesInFolder(params.folderPath)
        .filter(_.endsWith("zip"))
      println(f"Found ${zipFilesPath.size} archives to process.")

      val numPartitions: Int = params.numPartitions.getOrElse(zipFilesPath.size / 3)
      patentParsedRDD = sc.parallelize(zipFilesPath, numPartitions)
        .mapPartitions(part => parsePartitionsLocal(part))
        .filter( _._2 match {
          case Some(p) => true
          case None => false;
        })
        .map(_._2.get)
    }
    // Save it in parquet format (need to go through dataframe)
    patentParsedRDD.toDF()
      .write
      .mode(SaveMode.Append)
      .parquet(params.outputPath)

    // save list of processed archive
    zipPathRDD.coalesce(1).saveAsTextFile(params.outputPath + "/log_%s" format java.time.LocalDate.now.toString)
  }

/*  def run2(params: Params): Unit = {
    val conf = new SparkConf()
      .setAppName("GrobidSpark")
      .set("spark.driver.allowMultipleContexts", "true")

    val sc = SparkContext.getOrCreate(conf)
    sc.hadoopConfiguration.set("fs.s3a.connection.timeout", "500000")

    // sqlContext and implicits for dataframe
    val hc = new SQLContext(sc)
    import hc.implicits._

    var zipPathRDD: RDD[String] = sc.emptyRDD[String]
    var patentParsedRDD: RDD[PatentDocument] = sc.emptyRDD[PatentDocument]

    // Accumulator for errors
    val acc: Accumulable[mutable.MutableList[String], String] =
    sc.accumulableCollection(initialValue = mutable.MutableList())

    val zipFilesPath: List[String] = Helper.getRecursiveListOfFilesInFolder(params.folderPath)
      .filter(_.endsWith("zip"))
    println(f"Found ${zipFilesPath.size} archives to process.")

    val rdd: RDD[String] = sc.parallelize(zipFilesPath, 4).mapPartitions(
      x => parsePartStr(x))

    // rdd.saveAsTextFile("/tmp/xmlStr.txt")

    println(rdd.count())
  }*/

  def main(args: Array[String]): Unit = {

    // Argument parser
    val parser = new OptionParser[Params]("SparkPdfParser") {
      head("Spark Application that parse pdf in a folder and save it to a parquet file")

      opt[String]("folderPath").required()
        .text("path to folder containing pdf files to process")
        .action((x, c) => c.copy(folderPath = x))

      opt[String]("outputPath").required()
        .text("path to output parquet file")
        .action((x, c) => c.copy(outputPath = x))

      opt[Int]("numPartitions")
        .text("Number of partitions of rdd to process")
        .action((x, c) => c.copy(numPartitions = Some(x)))

      opt[Unit]("test")
        .text("Flag to test the software, process only 2 pdf archive")
        .action((_, c) => c.copy(test = true))

    }
    // parser.parse returns Option[C]
    parser.parse(args, Params()) match {
      case Some(params) => run(params)
      case None =>
        parser.showUsageAsError
        sys.exit(1)
    }
  }
}
