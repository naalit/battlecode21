FROM gitpod/workspace-full

RUN sudo apt-get update \
 && sudo apt-get install -y openjdk-8-jdk \
 && export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64 \
 && export PATH=$JAVA_HOME/bin:$PATH