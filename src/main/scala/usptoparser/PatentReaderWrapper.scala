package usptoparser

/**
  * Created by thomasopsomer on 25/05/2017.
  */
import java.io.Reader

import gov.uspto.parser.dom4j.Dom4j
import gov.uspto.patent.doc.greenbook.Greenbook
import gov.uspto.patent.doc.pap.PatentAppPubParser
import gov.uspto.patent.doc.sgml.Sgml
import gov.uspto.patent.doc.xml.ApplicationParser
import gov.uspto.patent.doc.xml.GrantParser
import gov.uspto.patent.PatentDocFormat
import gov.uspto.patent.PatentReaderException
import gov.uspto.patent.PatentReader
import gov.uspto.patent.model.Patent


class PatentReaderWrapper(private val patentFormat: PatentDocFormat) {

  private val maxByteSize = 100000000 // 100 MB.

  private lazy val parser: Dom4j = {
    patentFormat match {
      case PatentDocFormat.Greenbook => new Greenbook()
      case PatentDocFormat.RedbookApplication => new ApplicationParser()
      case PatentDocFormat.RedbookGrant => new GrantParser()
      case PatentDocFormat.Sgml => new Sgml()
      case PatentDocFormat.Pap => new PatentAppPubParser()
      case _ => throw new PatentReaderException("Invalid or Unknown Document Type")
    }
  }

  def read(reader: Reader): Patent = {
    if (!checkSize(reader)) {
     throw new PatentReaderException("Patent too Large")
    }
    parser match {
      case p: Greenbook => p.parse(reader)
      case _ => parser.parse(PatentReader.getJDOM(reader))
    }
  }

  def checkSize(reader: Reader): Boolean = {
    var charCount = 0
    var c = reader.read()
    while ( -1 != c ) {
      charCount += 1
      c = reader.read()
    }
    reader.reset()
    charCount * 2 < maxByteSize
  }
}
