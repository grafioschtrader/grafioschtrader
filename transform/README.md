## Transform
Transform is a desktop JavaFX application that allows to anonymize a large number of PDF files. For example, it can recursively import all PDFs in a file directory. Afterwards the text lines which are not relevant for the import can be removed in a mass processing. If your securities transactions are more or less completely available as PDF files, you can process them with Transform and then import them into GT.
### Prerequisite
* [Java JDK 11](https://jdk.java.net/java-se-ri/11): For build and execution
* [Apache Maven](https://maven.apache.org/): For the build

**Build and execute Transform**
````
# Directory transform
mvn clean install
# Execute in directory transform
java -jar ./target/grafioschtraderTransform-0.10.0.jar  
````
