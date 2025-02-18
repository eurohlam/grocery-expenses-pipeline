# grocery-expenses-pipeline

The idea of this project is to create a simple command-line pipeline that parses grocery receipts and convert them into 
key-value table that can be represented as a HashTable or a JSON
The project is implemented in Java 17 and uses: 
- [tesseract](https://github.com/tesseract-ocr/) as OCR (optical character recognition) engine
- [opencv4](https://opencv-java-tutorials.readthedocs.io/en/latest/) to process the receipt image in order to improve it for OCR.
Only [bilateralFilter](https://docs.opencv.org/4.x/dc/dd3/tutorial_gausian_median_blur_bilateral_filter.html) is used at the moment.

> [!NOTE]
> There are plenty of similar projects in github. The most of them are implemented in phyton, few in java. ChatGPT can parse
> receipts very well. However, it uses the same tesseract and pretty straight forward parsing rules behind the scene. Also, 
> free version of ChatGPT is limited to processing 2-3 images per day.  
> So, I decided that I can do my own implementation using my favourite Java.

## How to run tesseract via tess4j on MacOS

Install tessract via MAC port

    sudo port install tesseract

In your java application: 

Add VM argument
    
    -Djna.library.path=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib

Or add Env variable

    DYLD_LIBRARY_PATH=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib

Path to tessdata should be

    /opt/local/share/tessdata/

Maven dependency

        <dependency>
            <groupId>net.sourceforge.tess4j</groupId>
            <artifactId>tess4j</artifactId>
            <version>5.12.0</version>
        </dependency>


## How to run opencv4 via java

Install opencv4 with java support via MAC port

    sudo port install opencv4 +java

Check the installation

    sudo port contents opencv4 | grep java

If everything is fine tt should show:

    Port opencv4 @4.9.0_4+java contains:
    /opt/local/libexec/opencv4/java/jar/opencv-490.jar
    /opt/local/libexec/opencv4/java/jni/libopencv_java490.dylib

In your java application:

Add VM argument

    -Djava.library.path=/opt/local/libexec/opencv4/java/jni/

Your code must contain the following line before triggering opencv Imgcodecs

    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

Maven dependency

        <dependency>
            <groupId>org.openpnp</groupId>
            <artifactId>opencv</artifactId>
            <version>4.9.0-0</version>
        </dependency>

## How to run the pipeline

Build the jar

    mvn clean package

Run java

    java \
        -Djna.library.path=/opt/local/var/macports/software/tesseract/tesseract-5.4.1_2.darwin_22.x86_64/opt/local/lib  \
        -Djava.library.path=/opt/local/libexec/opencv4/java/jni/  \
    -jar target/grocery-expenses-pipeline-1.0-SNAPSHOT.jar  \
        -file src/test/resources/samples/IMG_5213.jpeg  \
        -restEndpoint http://localhost:8080  \
        -storeName PackNSave

where
* -file  -  is a path to an image that needs to be processed. This is a mandatory argument
* -restEndpoint  -  is a REST-endpoint where a result of recognition and parsing needs to be sent as a JSON. This is an optional argument.
* -storeName  -  is a name of store where you got current receipt. By default, it is "Undefined"