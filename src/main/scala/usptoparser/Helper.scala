package usptoparser

/**
  * Created by thomasopsomer on 24/05/2017.
  */

import scala.collection.JavaConversions._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectListing
import java.io.File
import scala.collection.mutable


object Helper {

  def listS3FolderKeys(s3Client: AmazonS3Client, s3Folder: String): List[(String, String)] = {
    // parse s3folder url into bucket and key
    val (bucketName, key) = splitBucketKey(s3Folder)
    // get the listing of s3 object
    var objectListing: ObjectListing = s3Client.listObjects(bucketName, key)
    var filesPathKeys = objectListing.getObjectSummaries.map(x => (bucketName, x.getKey))
    // while the list in not empty populate the list of keys
    while (objectListing.isTruncated) {
      objectListing = s3Client.listNextBatchOfObjects(objectListing)
      filesPathKeys ++= objectListing.getObjectSummaries.map(x => (bucketName, x.getKey))
    }
    // return as a list
    filesPathKeys.toList
  }

  def splitBucketKey(s3url: String): (String, String) = {
    val pattern = "^s3://([A-z-0-9-_]*)/(.*)".r
    val pattern(bucketName, key) = s3url.replace("s3a", "s3")
    (bucketName, key)
  }

  def isS3(path: String): Boolean = {
    path.startsWith("s3://") || path.startsWith("s3a://")
  }

  def getYearFromKey(key:String): Integer = {
    // ipg170103.zip
    val pattern = "^[a-z-A-Z]{2,6}([0-9]{6,8}).zip".r
    0
  }

  def getListOfSubDirectories(dir: File): List[String] = {
    val n0 = dir.listFiles
      .filter(_.isDirectory)
      .map(_.getAbsolutePath)
      .toList
    n0 ++ n0.map(x => new File(x)).flatMap(getListOfSubDirectories)
  }

  def getListOfFilesInFolder(dir: File): List[String] = {
    dir.listFiles.filter(_.isFile).map(_.getAbsolutePath).toList
  }

  def getRecursiveListOfFilesInFolder(folder: String): List[String]= {
    val dir: File = new File(folder)
    //
    val files: List[String] = Helper.getListOfFilesInFolder(dir)
    val subfiles: mutable.MutableList[String] = mutable.MutableList()
    for (folder <- Helper.getListOfSubDirectories(dir)) {
      val d = new File(folder)
      for (f <- Helper.getListOfFilesInFolder(d)) {
        subfiles += f
      }
    }
    files ++ subfiles
  }

}
