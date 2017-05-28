# uspto-parser

Spark application to parse patents from the USPTO. It mainly consists of a wrapper of the USPTO parser [USPTO/PatentPublicData](https://github.com/USPTO/PatentPublicData/), so thanks to them for the dirty work :)


## Build

Using [sbt docker](https://github.com/marcuslonnberg/sbt-docker) to directly get a docker image with the jar:
`sbt docker`


## Usage

show usage:

`docker run --rm asgard/uspto-parser:latest spark-submit /home/uspto-parser/uspto-parser-assembly-0.0.1.jar`

```
  --folderPath <value>
        path to folder containing patent archives to process
  --outputPath <value>
        path to output parquet file
  --numPartitions <value>
        Number of partitions of rdd to process
  --test
        Flag to test the software, process only 2 patent archive
  --from <value>
        Starting date, in string format, will be infered. For instance 20010101
  --to <value>
        Ending date, in string format: yyyyMMdd or yyMMdd
```


## Data Schema
```
root
 |-- type: string (nullable = true)
 |-- kind: string (nullable = true)
 |-- patentId: string (nullable = true)
 |-- patentNb: string (nullable = true)
 |-- applicationId: string (nullable = true)
 |-- applicationDate: string (nullable = true)
 |-- publicationDate: string (nullable = true)
 |-- relatedIds: array (nullable = true)
 |    |-- element: string (containsNull = true)
 |-- otherIds: array (nullable = true)
 |    |-- element: string (containsNull = true)
 |-- abstract: string (nullable = true)
 |-- briefSummary: string (nullable = true)
 |-- detailedDescription: string (nullable = true)
 |-- inventors: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- name: struct (nullable = true)
 |    |    |    |-- type: string (nullable = true)
 |    |    |    |-- raw: string (nullable = true)
 |    |    |    |-- firstName: string (nullable = true)
 |    |    |    |-- middleName: string (nullable = true)
 |    |    |    |-- lastName: string (nullable = true)
 |    |    |    |-- abbreviated: string (nullable = true)
 |    |    |-- address: struct (nullable = true)
 |    |    |    |-- street: string (nullable = true)
 |    |    |    |-- city: string (nullable = true)
 |    |    |    |-- state: string (nullable = true)
 |    |    |    |-- country: string (nullable = true)
 |    |    |    |-- zipCode: string (nullable = true)
 |    |    |    |-- email: string (nullable = true)
 |    |    |    |-- phone: string (nullable = true)
 |    |    |-- residency: string (nullable = true)
 |    |    |-- nationality: string (nullable = true)
 |-- applicants: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- name: struct (nullable = true)
 |    |    |    |-- type: string (nullable = true)
 |    |    |    |-- raw: string (nullable = true)
 |    |    |    |-- firstName: string (nullable = true)
 |    |    |    |-- middleName: string (nullable = true)
 |    |    |    |-- lastName: string (nullable = true)
 |    |    |    |-- abbreviated: string (nullable = true)
 |    |    |-- address: struct (nullable = true)
 |    |    |    |-- street: string (nullable = true)
 |    |    |    |-- city: string (nullable = true)
 |    |    |    |-- state: string (nullable = true)
 |    |    |    |-- country: string (nullable = true)
 |    |    |    |-- zipCode: string (nullable = true)
 |    |    |    |-- email: string (nullable = true)
 |    |    |    |-- phone: string (nullable = true)
 |-- assignees: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- name: struct (nullable = true)
 |    |    |    |-- type: string (nullable = true)
 |    |    |    |-- raw: string (nullable = true)
 |    |    |    |-- firstName: string (nullable = true)
 |    |    |    |-- middleName: string (nullable = true)
 |    |    |    |-- lastName: string (nullable = true)
 |    |    |    |-- abbreviated: string (nullable = true)
 |    |    |-- address: struct (nullable = true)
 |    |    |    |-- street: string (nullable = true)
 |    |    |    |-- city: string (nullable = true)
 |    |    |    |-- state: string (nullable = true)
 |    |    |    |-- country: string (nullable = true)
 |    |    |    |-- zipCode: string (nullable = true)
 |    |    |    |-- email: string (nullable = true)
 |    |    |    |-- phone: string (nullable = true)
 |    |    |-- role: string (nullable = true)
 |    |    |-- roleDesc: string (nullable = true)
 |-- ipcs: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- type: string (nullable = true)
 |    |    |-- main: boolean (nullable = true)
 |    |    |-- normalized: string (nullable = true)
 |    |    |-- section: string (nullable = true)
 |    |    |-- class: string (nullable = true)
 |    |    |-- subClass: string (nullable = true)
 |    |    |-- group: string (nullable = true)
 |    |    |-- subGroup: string (nullable = true)
 |-- claims: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- id: string (nullable = true)
 |    |    |-- type: string (nullable = true)
 |    |    |-- text: string (nullable = true)
 |    |    |-- parentIds: array (nullable = true)
 |    |    |    |-- element: string (containsNull = true)
 |-- priorities: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- docNumber: string (nullable = true)
 |    |    |-- kind: string (nullable = true)
 |    |    |-- date: string (nullable = true)
 |    |    |-- country: string (nullable = true)
 |    |    |-- id: string (nullable = true)
 |-- publicationRef: struct (nullable = true)
 |    |-- docNumber: string (nullable = true)
 |    |-- kind: string (nullable = true)
 |    |-- date: string (nullable = true)
 |    |-- country: string (nullable = true)
 |    |-- id: string (nullable = true)
 |-- applicationRef: struct (nullable = true)
 |    |-- docNumber: string (nullable = true)
 |    |-- kind: string (nullable = true)
 |    |-- date: string (nullable = true)
 |    |-- country: string (nullable = true)
 |    |-- id: string (nullable = true)
 |-- citations: array (nullable = true)
 |    |-- element: struct (containsNull = true)
 |    |    |-- num: string (nullable = true)
 |    |    |-- documentId: struct (nullable = true)
 |    |    |    |-- docNumber: string (nullable = true)
 |    |    |    |-- kind: string (nullable = true)
 |    |    |    |-- date: string (nullable = true)
 |    |    |    |-- country: string (nullable = true)
 |    |    |    |-- id: string (nullable = true)
 ```

