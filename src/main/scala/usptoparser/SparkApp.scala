package usptoparser

/**
  * Created by thomasopsomer on 23/05/2017.
  */


import java.io.{File, StringReader}

import org.apache.spark.{Accumulable, SparkConf, SparkContext}
import org.apache.spark.sql.{SQLContext, SaveMode}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.model.GetObjectRequest
import org.apache.spark.rdd.RDD
import scala.collection.mutable
import scala.util.Try
import scopt.OptionParser
import org.apache.log4j.LogManager


object SparkApp {

  case class Params(
                     folderPath: String = null,
                     outputPath: String = null,
                     numPartitions: Option[Int] = None,
                     test: Boolean = false
                   )

  @transient lazy val logger = LogManager.getLogger("SparkUsptoParser")

  def parsePartitionsS3(partition: Iterator[(String, String)],
                        tmpZipFolder: String = "/tmp/uspto-zip/"): Iterator[Option[PatentDocument]] = {
    val zipDir = new File(tmpZipFolder)
    if (!zipDir.exists) {
      logger.debug(f"Creating temporary folder at ${zipDir.getAbsolutePath}")
      zipDir.mkdirs
    }
    val s3client: AmazonS3Client = new AmazonS3Client()
    try {
      partition.flatMap(x => {
        // DL file from S3
        val (bucket, key) = x
        val path: String = tmpZipFolder + key.split("/").last
        val file = new File(path)
        logger.debug(f"Downloading file ${file.getName} from s3://$bucket/$key")
        s3client.getObject(new GetObjectRequest(bucket, key), file)
        logger.info(f"Done downloading file ${file.getName} to ${file.getAbsolutePath}")
        // parse it
        val patentsIt: Iterator[Option[PatentDocument]] = UsptoParserWrapper.parseZipFileIt(file, delete=true)
        patentsIt
      })
    }
    finally {
      // remove folder if empty
      zipDir.delete
    }
  }

  def parsePartition(partition: Iterator[String]): Iterator[Option[PatentDocument]] = {
    partition.flatMap( x => {
      val file = new File(x)
      val patentsIt = UsptoParserWrapper.parseZipFileIt(file, delete=false)
      patentsIt
    })
  }

  def run(params: Params) = {

    val conf = new SparkConf()
      .setAppName("GrobidSpark")
      .set("spark.driver.allowMultipleContexts", "true")

    val sc = SparkContext.getOrCreate(conf)
    // sc.setLogLevel("INFO")
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
      logger.info("Detecting a s3 input folder.")
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
      logger.info(f"Found logs for ${archive_logs.size} parsed files")

      // get list of s3 keys, and filter them
      var bucketKeysList: List[(String, String)] = Helper.listS3FolderKeys(s3, folderPath)
        .filter(x => x._2.endsWith("zip"))
        .filter(x => !archive_logs.contains(x._2))
      bucketKeysList = if (params.test) bucketKeysList.slice(0, 4) else bucketKeysList
      // shutdown s3 client
      s3.shutdown()

      if (bucketKeysList.nonEmpty) {
        logger.info(f"Found ${bucketKeysList.size} files to parse after removing already parsed ones from the logs")
        // handle number of partitions
        val numPartitions = params.numPartitions.getOrElse(bucketKeysList.size / 3)

        val zipBucketKeyRDD: RDD[(String, String)] = sc.parallelize(bucketKeysList, numPartitions)

        patentParsedRDD = zipBucketKeyRDD
          .mapPartitions(part => parsePartitionsS3(part))
          .filter {
            case Some(p) => true;
            case None => false;
          }
          .map(_.get)
        // just for log
        zipPathRDD = zipBucketKeyRDD.map(x => f"s3://${x._1}/${x._2}")
      }
      else {
        logger.info("Found no new file to parse")
        sys.exit(1)
      }
    }
    else {
      // val folderPath = "s3://asgard-data/test/patent/patzip/"
      logger.info("Detecting an local input folder.")
      val zipFilesPath: List[String] = Helper.getRecursiveListOfFilesInFolder(params.folderPath)
        .filter(_.endsWith("zip"))
      logger.info(f"Found ${zipFilesPath.size} archives to process.")

      val numPartitions: Int = params.numPartitions.getOrElse(zipFilesPath.size / 3)

      val rdd = sc.parallelize(zipFilesPath, numPartitions)
        .mapPartitions(part => parsePartition(part))

      patentParsedRDD = sc.parallelize(zipFilesPath, numPartitions)
        .mapPartitions(part => parsePartition(part))
        .filter {
          case Some(p) => true
          case None => false;
        }
        .map(_.get)
    }

    // Save it in parquet format (need to go through dataframe)
    logger.info(f"Starting to parse files, appending parquet ${params.outputPath}")
    patentParsedRDD.toDF()
      .write
      .mode(SaveMode.Append)
      .parquet(params.outputPath)
    logger.info(f"Done parsing and appending parquet")

    // save list of processed archive
    val logPath = params.outputPath + "/log_%s" format java.time.LocalDate.now.toString
    zipPathRDD.coalesce(1).saveAsTextFile(logPath)
    logger.info(f"Log file save to $logPath")
  }

  def main(args: Array[String]): Unit = {

    // Argument parser
    val parser = new OptionParser[Params]("SparkPdfParser") {
      head("Spark Application that parse archive of uspto patent in a folder and save it to a parquet file")

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
