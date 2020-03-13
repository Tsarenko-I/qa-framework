package s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import java.io.*;

public class AWSSDKServise {

    public static void downloadFile() throws IOException {
        String bucketName = "cinfra-prod-qa-autotest-data";
        String key = "testFileHW.txt";
        String filePath ="/Users/igor/Desktop/qa-fraimework/qa-api/target/testFile.txt";

        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

        System.out.println("Downloading an object");
        S3Object document = s3Client.getObject(new GetObjectRequest(bucketName, key));
        InputStream initialStream = document.getObjectContent();
        File targetFile = new File(filePath);
        OutputStream outStream = new FileOutputStream(targetFile);
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = initialStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }
    }


    public static void uploadFile() {
        final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        String bucket = "cinfra-prod-qa-autotest-data";
        String key = "testFileHW.txt";
        String filePath = "/Users/igor/Desktop/qa-fraimework/qa-api/src/main/resources/testFileText.txt";
        s3.putObject(bucket, key, new File(filePath));

    }
}
