/**
  * Created by thomasopsomer on 24/05/2017.
  */
import java.io.{File, StringReader}

import gov.uspto.patent.model.{DescSection, Patent}
import gov.uspto.patent.{DateTextType, PatentDocFormat, PatentReader}
import usptoparser.UsptoParserWrapper

object UsptoParserWrapperTest {
  def main() = {
    /*
    val path = "/Users/thomasopsomer/data/uspto2/pg041228.zip"
    val path = "/Users/thomasopsomer/data/ipa130711.zip"
    val inputFile = new File(path)

    val r = UsptoParserWrapper.parseZipFileIt(inputFile)

    val patentDocFormat: PatentDocFormat = UsptoParserWrapper.getPatentDocFormat(inputFile)

    val dumpReader = UsptoParserWrapper.getDumpReader(inputFile)
    dumpReader.open()

    val xmlStr = dumpReader.next
    val xmlStrReader = new StringReader(xmlStr)
    val patentReader = new PatentReader(patentDocFormat)
    val dom = PatentReader.getJDOM(xmlStrReader)
    // parse patent
    val patent: Patent = patentReader.read(xmlStrReader)

    import gov.uspto.patent.doc.xml.ApplicationParser
    import gov.uspto.patent.doc.xml.fragments.PriorityClaims
    import gov.uspto.patent.doc.xml.items.DocumentIdNode
    import gov.uspto.patent.model.DocumentId

    val parser = new ApplicationParser()
    val dom: Document = PatentReader.getJDOM(xmlStrReader)
    parser.parse(dom)



    val pp = new PriorityClaims(dom)
    pp.read()

    import org.dom4j.Document
    import org.dom4j.Node
    import gov.uspto.patent.model.CountryCode

    val pnodes: List[Node] = dom.selectNodes("//priority-claims/priority-claim").asScala.toList.asInstanceOf[List[Node]]
    new DocumentIdNode(pnodes(0))

    val docNumN = pnodes(0).selectSingleNode("doc-number")
    val countryN = pnodes(0).selectSingleNode("country")
    val kindN = pnodes(0).selectSingleNode("kind")

    val kindCode = if (kindN != null) kindN.getText() else null
    val countryCode = CountryCode.fromString(countryN.getText)
    val documentId = new DocumentId(countryCode, docNumN.getText(), kindCode)



    UsptoParserWrapper.toDoc(patent)

    val relatedIds = patent.getRelationIds.asScala.map(x => x.getId).toList
    val otherIds = patent.getOtherIds.asScala.map(x => x.getId).toList

    val description = patent.getDescription
    val briefSummary: String = description.getSection(DescSection.BRIEF_SUMMARY).getPlainText(UsptoParserWrapper.freeTextConfig)
    val detailedDescription: String = description.getSection(DescSection.DETAILED_DESC)
      .getPlainText(freeTextConfig)

    patent.getPatentType.toString,
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
    `abstract` = patent.getAbstract.getPlainText,
    briefSummary = briefSummary,
    detailedDescription = detailedDescription,
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
    */
  }
}
