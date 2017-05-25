package usptoparser

/**
  * Created by thomasopsomer on 23/05/2017.
  */


import java.io.{File, StringReader}

import gov.uspto.common.filter.FileFilterChain
import gov.uspto.patent.bulk.{DumpFileAps, DumpFileXml, DumpReader}
import gov.uspto.patent.{DateTextType, PatentDocFormat, PatentDocFormatDetect, PatentReader}
import gov.uspto.patent.model._
import gov.uspto.patent.model.classification._
import gov.uspto.patent.model.entity.Address
import gov.uspto.patent.model.entity.NameOrg
import gov.uspto.patent.model.entity.NamePerson
import gov.uspto.patent.doc.simplehtml.FreetextConfig
import org.apache.commons.io.filefilter.SuffixFileFilter

import scala.collection.JavaConverters._
import scala.collection.mutable


class UsptoParserWrapper(inputFile: File) extends Serializable {

  private val dumpReader = UsptoParserWrapper.getDumpReader(inputFile)
  private val patentDocFormat= UsptoParserWrapper.getPatentDocFormat(inputFile)
  private val patentReaderWrapper = new PatentReaderWrapper(patentDocFormat)

  def parseZipFile(): mutable.ArrayBuffer[Option[PatentDocument]] = {
    // container to place the parsed patent
    val key = inputFile.getName
    val container = mutable.ArrayBuffer.empty[Option[PatentDocument]]
    // zip file reader
    dumpReader.open()
    // iterate over the big xml
    try {
      while (dumpReader.hasNext) {
        try {
          // get xml of patent
          val xmlStr = dumpReader.next
          if (xmlStr != null) {
            val xmlStrReader = new StringReader(xmlStr)
            val patent = patentReaderWrapper.read(xmlStrReader)
            // transform to custom PatentDocument object (case class)
            val doc = UsptoParserWrapper.toDoc(patent)
            container += Some(doc)
          }
        }
        catch {
          case e: Exception => {e.printStackTrace(); println(key);}
        }
      }
    }
    finally {
      dumpReader.close()
      println(f"finish file $key")
    }
    container
  }

  def parseZipFileIt(): Iterator[Option[PatentDocument]] = {

    val containerIt = Iterator.empty

    val key = inputFile.getName
    // zip file reader
    dumpReader.open()

    try {
      while (dumpReader.hasNext) {
        try {
          // get xml of patent
          val xmlStr = dumpReader.next
          if (xmlStr != null) {
            val xmlStrReader = new StringReader(xmlStr)
            val patent = patentReaderWrapper.read(xmlStrReader)
            // transform to custom PatentDocument object (case class)
            val doc = UsptoParserWrapper.toDoc(patent)
            // container += Some(doc)
            containerIt ++ Iterator(Some(doc))
          }
        }
        catch {
          case e: Exception => {e.printStackTrace();}
        }
      }
    }
    finally {
      dumpReader.close()
      println(f"finish file $key")
    }
    containerIt
  }


  def dumpReaderIterator: Iterator[String] = {
    Iterator.continually(dumpReader.next).takeWhile(_ => dumpReader.hasNext)
  }

}


object UsptoParserWrapper {

  def parseZipFile(inputFile: File): List[(String, Option[PatentDocument])] = {
    // container to place the parsed patent
    val key = inputFile.getName
    val container = mutable.ArrayBuffer.empty[(String, Option[PatentDocument])]
    // patent format
    val patentDocFormat = getPatentDocFormat(inputFile)
    // zip file reader
    val dumpReader = getDumpReader(inputFile, patentDocFormat)
    dumpReader.open()
    // iterate over the big xml
    try {
      while (dumpReader.hasNext) {
        try {
          // get xml of patent
          val xmlStr = dumpReader.next
          val xmlStrReader = new StringReader(xmlStr)
          val patentReader = new PatentReader(patentDocFormat)
          // parse patent
          val patent: Patent = patentReader.read(xmlStrReader)
          // transform to custom PatentDocument object (case class)
          val doc = UsptoParserWrapper.toDoc(patent)
          container += Tuple2(key, Some(doc))
        }
        catch {
          case e: Exception => {e.printStackTrace(); println(key);}
        }
      }
    }
    finally {
      // dumpReader.close()
      println(f"finish file $key")
    }
    container.toList
  }

  def getDumpReader(inputFile: File): DumpReader = {
    val patentDocFormat: PatentDocFormat = getPatentDocFormat(inputFile)
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

  def getDumpReader(inputFile: File, patentDocFormat: PatentDocFormat): DumpReader = {
    getDumpReader(inputFile: File)
  }

  def getPatentDocFormat(inputFile: File): PatentDocFormat = {
    new PatentDocFormatDetect().fromFileName(inputFile)
  }

  lazy val freeTextConfig: FreetextConfig = {
    val badHtmlElem = List("fig", "tr", "table", "br", "dd", "dt")
    val config = new FreetextConfig()
    for (elem <- badHtmlElem) {
      config.remove(elem)
    }
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
    usptoparser.DocumentId(
      kind = docId.getKindCode,
      docNumber = docId.getDocNumber,
      country = valueOrEmpty(docId.getCountryCode),
      date = docId.getDate.getDateText(DateTextType.ISO),
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
