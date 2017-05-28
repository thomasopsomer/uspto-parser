FROM asgard/spark:latest

# Install sbt
ENV SBT_VERSION 0.13.8
RUN \
  curl -L -o sbt-$SBT_VERSION.deb http://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

# add code and compile it
ADD . /home/uspto-parser
WORKDIR /home/uspto-parser

RUN sbt assembly
RUN mkdir /tmp/spark-events

# spark-submit target/scala-2.10/uspto-parser-assembly-0.0.1.jar ...
CMD ["/sbin/my_init"]

# clean docker
RUN apt-get autoremove -y \
  && apt-get clean -y \
  && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*