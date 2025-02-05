## How to run tesseract via tess4j on MacOS

Install tessract via MAC port

    sudo port install tesseract

In your java application 

Add VM argument
    
    -Djna.library.path=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib

Or add Env variable

    DYLD_LIBRARY_PATH=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib

Path to tessdata should be

    /opt/local/share/tessdata/


## How to run opencv4 via java

Install opencv4 with java support via MAC port

    sudo port install opencv4 +java

Check the installation

    sudo port contents opencv4 | grep java

If everything is fine tt should show:

    Port opencv4 @4.9.0_4+java contains:
    /opt/local/libexec/opencv4/java/jar/opencv-490.jar
    /opt/local/libexec/opencv4/java/jni/libopencv_java490.dylib

In your java application

Add VM argument

    -Djava.library.path=/opt/local/libexec/opencv4/java/jni/

Your code must contain the following line before triggering opencv Imgcodecs

    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

## How to run the pipeline

Build the jar

    mvn clean package

Run java

    java \
        -Djna.library.path=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib  \
        -Djava.library.path=/opt/local/libexec/opencv4/java/jni/  \
    -jar target/grocery-expenses-pipeline-1.0-SNAPSHOT.jar  \
        -file src/test/resources/samples/IMG_5213.jpeg  \
        -restEndpoint http://localhost:8080