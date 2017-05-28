package usptoparser

/**
  * Created by thomasopsomer on 25/05/2017.
  */
import java.io.{Reader, StringReader}

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
import org.dom4j.Document
import org.dom4j.DocumentException
import org.dom4j.io.SAXReader
import org.xml.sax.SAXException
import org.apache.commons.io.IOUtils
import java.io.IOException


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

/*  def read(reader: Reader): Patent = {
    if (!checkSize(reader)) {
     throw new PatentReaderException("Patent too Large")
    }
    parser match {
      case p: Greenbook => p.parse(reader)
      // case _ => parser.parse(PatentReader.getJDOM(reader))
      case _ => parser.parse(getJDOM(reader))
    }
  }*/

  def read(xmlStr: String): Patent = {
    val reader = new StringReader(xmlStr)

    if (!checkSize(reader)) {
      throw new PatentReaderException("Patent too Large")
    }
    parser match {
      case p: Greenbook => p.parse(reader)
      // case _ => parser.parse(PatentReader.getJDOM(reader))
      case _ => {
        val dom: Document = {
          try {
            saxReader.read(reader)
          }
          catch {
            case e @ (_: DocumentException | _: SAXException) =>
              try {
                // reload it instead of reseting it because if causes error in spark :/
                // >> java.io.IOException: Stream closed
                // maybe because of StringReader not being thread safe, at least for "reseting" the stream...
                val newReader = new StringReader(xmlStr)
                PatentReader.fixTagsJDOM(IOUtils.toString(newReader))
              }
              catch {
                case e: IOException => throw new PatentReaderException(e)
              }
          }
        }
        parser.parse(dom)
      }
    }
  }

  private def checkSize(reader: Reader): Boolean = {
    var charCount = 0
    var c = reader.read()
    while ( -1 != c ) {
      charCount += 1
      c = reader.read()
    }
    reader.reset()
    charCount * 2 < maxByteSize
  }

  private lazy val saxReader = {
    val sax = new SAXReader(false)
    sax.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    sax
  }

  private def getJDOM(reader: Reader): Document = {
    try {
      // val sax = new SAXReader(false)
      // sax.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      saxReader.read(reader)
    }
    catch {
      case e @ (_: DocumentException | _: SAXException) =>
        try {
          reader.reset()
          PatentReader.fixTagsJDOM(IOUtils.toString(reader))
        }
        catch {
          case e: IOException => throw new PatentReaderException(e)
        }
    }
  }
}
