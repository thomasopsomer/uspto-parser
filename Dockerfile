FROM asgard/spark:latest

# install sbt
RUN \
  echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
  apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
  apt-get -y update && \
  apt-get install -y sbt && \
  sbt sbtVersion

# add code and compile it
ADD . /home/uspto-parser
WORKDIR /home/uspto-parser

RUN sbt assembly
RUN mkdir /tmp/spark-events

# spark-submit target/scala-2.10/uspto-parser-assembly-0.0.1.jar ...
CMD [/sbin/init_script]

# clean docker
RUN apt-get autoremove -y \
  && apt-get clean -y \
  && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*