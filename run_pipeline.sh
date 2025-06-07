#!/bin/bash

####################################################################
# Runs the pipeline for each image iterating through the input folder
####################################################################

echo "Input args: "$@
INPUT_FOLDER=$1
# if INPUT_FOLDER is null then set to default folder "input"
test -z $INPUT_FOLDER && INPUT_FOLDER=input

#JAVA_HOME=
TESSERACT_LIB_PATH=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib
TESS_DATA=/opt/local/share/tessdata/
OPENCV_LIB_PATH=/opt/local/libexec/opencv4/java/jni/
JAR_VERSION=1.0-SNAPSHOT
REST_ENDPOINT=http://localhost:7080/api/receipt/

echo "Scanning input folder: $INPUT_FOLDER"

for storeName in $INPUT_FOLDER/*
do
  echo "Parsing receipts for storeName: "$(basename $storeName)

  for fileName in $storeName/*
  do
    echo ""
    echo "################################"
    echo "Running pipeline for: "$fileName
    echo "################################"

    echo "storeName: "$(basename $storeName)" filename: "$fileName

    $JAVA_HOME/bin/java \
       -Djna.library.path=$TESSERACT_LIB_PATH  \
       -Djava.library.path=$OPENCV_LIB_PATH  \
    -jar target/grocery-expenses-pipeline-$JAR_VERSION.jar  \
       -file $fileName  \
       -tessdataPath $TESS_DATA  \
       -storeName $(basename $storeName)  \
       -restEndpoint  $REST_ENDPOINT

    echo ""
    echo "Finished pipeline for: "$fileName
  done
done

