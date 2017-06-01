package usptoparser

/**
  * Created by thomasopsomer on 23/05/2017.
  */


import java.io.File

import gov.uspto.common.filter.FileFilterChain
import gov.uspto.patent.bulk.{DumpFileAps, DumpFileXml, DumpReader}
import gov.uspto.patent.{DateTextType, PatentDocFormat, PatentDocFormatDetect}
import gov.uspto.patent.model.{CitationType, DescSection, PatCitation, Patent}
import gov.uspto.patent.model.classification._
import gov.uspto.patent.model.entity.Address
import gov.uspto.patent.model.entity.NameOrg
import gov.uspto.patent.model.entity.NamePerson
import gov.uspto.patent.doc.simplehtml.{FreetextConfig, HtmlFieldType}
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.apache.log4j.LogManager

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer


object UsptoParserWrapper {

  @transient lazy val logger = LogManager.getLogger("SparkUsptoParser.UsptoParserWrapper")

  def parseZipFileIt(file: File, delete: Boolean = false): Iterator[Option[PatentDocument]] = {
    //
    logger.debug(f"Processing file ${file.getName}")
    new UsptoParserWrapper(file, delete)
  }

  def parseZipFile(file: File, delete: Boolean = false): ArrayBuffer[Option[PatentDocument]] = {

    logger.debug(f"Processing file ${file.getName}")
    val patentFormat = getPatentDocFormat(file)
    logger.debug(f"Detected file format ${patentFormat.toString}")
    val dumpReader = getDumpReader(file, patentFormat)
    logger.debug("Dump reader created")
    // val patentReader = new PatentReader(patentFormat)
    val patentReader = new PatentReaderWrapper(patentFormat)
    // empty container (array buffer)
    var container = ArrayBuffer.empty[Option[PatentDocument]]
    dumpReader.open()

    try {
      while (dumpReader.hasNext) {
        try {
          // get xml of patent
          val xmlStr = dumpReader.next
          if (xmlStr != null) {
            // val xmlStrReader = new StringReader(xmlStr)
            // val patent = patentReader.read(xmlStrReader)
            val patent: Patent = patentReader.read(xmlStr)
            // val patent = patentReaderWrapper.read(xmlStr)
            // transform to custom PatentDocument object (case class)
            val doc = toDoc(patent)
            // logger.debug(f"Patent number: ${doc.patentId}")
            container += Some(doc)
          }
          else {
            container += None
          }
        }
        catch {
          case e: Exception => {e.printStackTrace(); println(file.getName);}
        }
      }
      logger.info(f"Done processing file ${file.getName}")
    }
    finally {
      dumpReader.close()
      if (delete) file.delete()
    }
    container
  }

  def getDumpReader(inputFile: File): DumpReader = {
    val patentDocFormat: PatentDocFormat = getPatentDocFormat(inputFile)
    getDumpReader(inputFile, patentDocFormat)
  }

  def getDumpReader(inputFile: File, patentDocFormat: PatentDocFormat): DumpReader = {
    // getDumpReader(inputFile: File)
    val dumpReader: DumpReader = patentDocFormat match {
      case PatentDocFormat.Greenbook => new DumpFileAps(inputFile)
      case _ => {
        val dumpReader: DumpReader = new DumpFileXml(inputFile)
        val filters = new FileFilterChain()
        //filters.addRule(new PathFileFilter(""));
        filters.addRule(new SuffixFileFilter("xml"))
        dumpReader.setFileFilter(filters)
        dumpReader
      }
    }
    dumpReader
  }

  def getPatentDocFormat(inputFile: File): PatentDocFormat = {
    new PatentDocFormatDetect().fromFileName(inputFile)
  }

  lazy val freeTextConfig: FreetextConfig = {
    // val badHtmlElem = List("fig", "tr", "table", "br", "dd", "dt")
    val config = new FreetextConfig()
    // remove tables, maths formula...
    config.remove(HtmlFieldType.TABLE)
    config.remove(HtmlFieldType.MATHML)
    config.remove(HtmlFieldType.CROSSREF)
    // replace citation, figure links ...
    config.replace(HtmlFieldType.FIGREF, "Patent-Figure")
    config.replace(HtmlFieldType.CLAIMREF, "Patent-Claim")
    config.replace(HtmlFieldType.PATCITE, "Patent-Citation")
    config.replace(HtmlFieldType.NPLCITE, "Patent-Citation")

    config
  }

  def toDoc(patent: Patent): PatentDocument = {

    val relatedIds = patent.getRelationIds.asScala.map(x => x.getId).toList
    val otherIds = patent.getOtherIds.asScala.map(x => x.getId).toList

    PatentDocument(
      `type` = valueOrEmpty(patent.getPatentType),
      kind = patent.getDocumentId.getKindCode,
      // ids
      patentId = patent.getDocumentId.getId,
      patentNb = patent.getDocumentId.getDocNumber,
      applicationId = patent.getApplicationId.getId,
      // dates
      publicationDate = patent.getDatePublished.getDateText(DateTextType.ISO),
      applicationDate = patent.getApplicationDate.getDateText(DateTextType.ISO),
      // related publications
      relatedIds = relatedIds,
      otherIds =otherIds,
      // fulltext
      `abstract` = patent.getAbstract.getPlainText.trim,
      briefSummary = getBriefSummary(patent),
      detailedDescription = getDetailedDescription(patent),
      // classifications
      ipcs = mapClassifications(patent),
      // parties
      inventors = mapInventors(patent),
      applicants = mapApplicants(patent),
      assignees = mapAssignees(patent),
      // claims
      claims = mapClaims(patent),
      // full doc reference
      priorities = mapPriorities(patent),
      publicationRef = mapDocumentId(patent.getDocumentId),
      applicationRef = mapDocumentId(patent.getApplicationId),
      //
      citations = mapCitations(patent)
    )
  }

  def mapApplicants(patent: Patent): List[usptoparser.Applicant] = {
    val applicants = patent.getApplicants.asScala.map(x => {
      usptoparser.Applicant(
        name = mapName(x.getName),
        address = mapAddress(x.getAddress)
      )
    }).toList
    if (applicants.nonEmpty) applicants else null
  }

  def mapInventors(patent: Patent): List[usptoparser.Inventor] = {
    val inventors = patent.getInventors.asScala.map(x => {
      usptoparser.Inventor(
        name = mapName(x.getName),
        address = mapAddress(x.getAddress),
        residency = valueOrEmpty(x.getResidency),
        nationality = valueOrEmpty(x.getNationality)
      )
    }).toList
    if (inventors.nonEmpty) inventors else null
  }

  def mapAssignees(patent: Patent): List[usptoparser.Assignee] = {
    val assignees = patent.getAssignee.asScala.map(x => {
      usptoparser.Assignee(
        name = mapName(x.getName),
        address = mapAddress(x.getAddress),
        role = x.getRole,
        roleDesc = x.getRoleDesc
      )
    }).toList
    if (assignees.nonEmpty) assignees else null
  }

  def mapName(name: gov.uspto.patent.model.entity.Name): usptoparser.Name = {
    name match {
      case perName: NamePerson => {
        usptoparser.Name(
        `type` = "Person",
        raw = name.getName,
        firstName = perName.getFirstName,
        middleName = perName.getMiddleName,
        lastName = perName.getLastName,
        abbreviated = perName.getAbbreviatedName
        )
      }
      case orgName: NameOrg => {
        usptoparser.Name(
          `type` = "Org",
          raw = name.getName
        )
      }
    }
  }

  def mapAddress(address: Address): usptoparser.AddressBook = {
    if (address != null) {
      usptoparser.AddressBook(
        street = valueOrEmpty(address.getStreet),
        city = valueOrEmpty(address.getCity),
        state = valueOrEmpty(address.getState),
        country = valueOrEmpty(address.getCountry),
        zipCode = valueOrEmpty(address.getZipCode),
        email = valueOrEmpty(address.getEmail),
        phone = valueOrEmpty(address.getPhoneNumber)
      )}
    else {
      null
    }
  }

  def mapClassifications(patent: Patent): List[usptoparser.IPC] = {
    patent.getClassification.asScala
      .filter(x => x.getType == ClassificationType.IPC)
      .map(x => mapClassification(x)).toList
  }

  def mapClassification(cla: PatentClassification): usptoparser.IPC = {
    cla match {
      case ipc: IpcClassification => {
        // main ipc
        val section = ipc.getSection
        val classe = if (section != null) section + ipc.getMainClass else null
        val subClass = if (classe != null) classe + ipc.getMainClass else null
        val group = if (subClass != null) subClass + "-" + ipc.getMainGroup else null
        val subGroup = if (group != null) group + "/" + ipc.getSubGroup else null
        usptoparser.IPC(
          `type` = "ipc",
          main = true,
          normalized = ipc.getTextNormalized,
          section = section,
          `class` = classe,
          subClass = subClass,
          group = group,
          subGroup = subGroup
        )
      }
    }
  }

  def mapClaims(patent: Patent): List[usptoparser.Claim] = {
    val claims = patent.getClaims.asScala.map(x => {
      usptoparser.Claim(
        id = x.getId,
        `type` = valueOrEmpty(x.getClaimType),
        text = x.getPlainText,
        parentIds = if (x.getDependentIds != null) x.getDependentIds.asScala.toList else null
      )
    }).toList
    if (claims.nonEmpty) claims else null
  }

  def mapPriorities(patent: Patent): List[usptoparser.DocumentId] = {
    patent.getPriorityIds.asScala.map(x => mapDocumentId(x)).toList
  }

  def mapDocumentId(docId: gov.uspto.patent.model.DocumentId): usptoparser.DocumentId = {
    val date = if (docId.getDate != null) docId.getDate.getDateText(DateTextType.ISO) else null
    usptoparser.DocumentId(
      kind = docId.getKindCode,
      docNumber = docId.getDocNumber,
      country = valueOrEmpty(docId.getCountryCode),
      date = date,
      id = docId.getId
    )
  }

  def mapCitations(patent: Patent): List[Citation] = {
    val citations = patent.getCitations.asScala
      .filter(x => x.getCitType == CitationType.PATCIT)
      .map {
        case cit: PatCitation =>
          usptoparser.Citation(
            num = cit.getNum,
            documentId = mapDocumentId(cit.getDocumentId)
          )
    }.toList
    if (citations.nonEmpty) citations else null
  }

  def getBriefSummary(patent: Patent) = {
    Option(patent.getDescription.getSection(DescSection.BRIEF_SUMMARY)) match {
      case Some(d) => d.getPlainText(freeTextConfig).trim
      case _ => null
    }
  }

  def getDetailedDescription(patent: Patent) = {
    Option(patent.getDescription.getSection(DescSection.DETAILED_DESC)) match {
      case Some(d) => d.getPlainText(freeTextConfig).trim
      case _ => null
    }
  }

  def valueOrEmpty(value: AnyRef): String = {
    if (value != null) value.toString else null
  }

}


class UsptoParserWrapper(file: File, delete: Boolean = false) extends Iterator[Option[PatentDocument]] {

  import UsptoParserWrapper.logger

  private val patentDocFormat = {
    val format = UsptoParserWrapper.getPatentDocFormat(file)
    logger.debug(f"Detected file format ${format.toString}")
    format
  }

  private val dumpReader = {
    logger.debug("Creating dump reader")
    UsptoParserWrapper.getDumpReader(file, patentDocFormat)
  }

  private val patentReader = new PatentReaderWrapper(patentDocFormat)

  def hasNext = {
    val hasNext = dumpReader.hasNext
    if (!hasNext) {
      logger.debug(f"Done processing file ${file.getName}")
      // close the file
      close()
      // remove the file if asked
      if (delete) file.delete()
    }
    hasNext
  }

  var isOpen = false

  def open() = {
    dumpReader.open()
    isOpen = true
  }

  def close() = {
    dumpReader.close()
    isOpen = false
  }

  def next() = {
    // check if reader is open
    if (!isOpen) open()

    val xmlStr = dumpReader.next

    if (xmlStr != null) {
      val patent: Patent = patentReader.read(xmlStr)
      // transform to custom PatentDocument object (case class)
      val doc = UsptoParserWrapper.toDoc(patent)
      // logger.debug(f"Patent number: ${doc.patentId}")
      Some(doc)
    }
    else {
      None
    }
  }
}