FROM asgard/spark:latest


ADD . /home/uspto-parser
WORKDIR /home/uspto-parser

RUN sbt assembly
RUN mkdir /tmp/spark-events

# spark-submit target/scala-2.10/uspto-parser-assembly-0.0.1.jar ...
CMD [/sbin/init_script]

