/**
  * Created by thomasopsomer on 23/05/2017.
  */
package usptoparser


case class PatentDocument(
                         `type`: String = null,
                         kind: String = null,
                         // ids
                         patentId: String,
                         patentNb: String = null,
                         applicationId: String = null,
                         // date
                         applicationDate: String = null,
                         publicationDate: String = null,
                         // related publications
                         relatedIds: List[String] = null,
                         otherIds: List[String] = null,
                         // descriptions
                         `abstract`: String = null,
                         briefSummary: String = null,
                         detailedDescription: String = null,
                         inventors: List[Inventor] = null,
                         applicants: List[Applicant] = null,
                         assignees: List[Assignee] = null,
                         //
                         ipcs: List[IPC] = null,
                         //
                         claims: List[Claim] = null,
                         //full doc refs
                         priorities: List[DocumentId] = null,
                         publicationRef: DocumentId = null,
                         applicationRef: DocumentId = null,
                         //
                         citations: List[Citation] = null
                         )


case class DocumentId(
                     docNumber: String,
                     kind: String,
                     date: String,
                     country: String,
                     id: String = null
                     )


case class Inventor(
                 name: Name,
                 address: AddressBook,
                 residency: String,
                 nationality: String
                 )


case class Assignee(
                     name: Name,
                     address: AddressBook,
                     role: String,
                     roleDesc: String
                   )


case class Applicant(
                     name: Name,
                     address: AddressBook
                   )


case class Name(
               `type`: String,
               raw: String,
               firstName: String = null,
               middleName: String  = null,
               lastName: String  = null,
               abbreviated: String = null
               )


case class AddressBook(
                      street: String = null,
                      city: String = null,
                      state: String = null,
                      country: String = null,
                      zipCode: String = null,
                      email: String = null,
                      phone: String = null
                 )


case class Claim(
                id: String,
                `type`: String,
                text: String,
                parentIds: List[String]
                )


case class IPC(
              `type`: String,
              main: Boolean,
              normalized: String,
              section:String,
              `class`: String,
              subClass: String,
              group:String,
              subGroup: String
              )


case class Citation(
                   num: String,
                   documentId: DocumentId
                   )