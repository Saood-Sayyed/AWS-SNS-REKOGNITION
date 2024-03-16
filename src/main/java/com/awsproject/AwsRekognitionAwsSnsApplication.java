package com.awsproject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Scanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.SubscribeRequest;

@RestController
@SpringBootApplication
public class AwsRekognitionAwsSnsApplication {

    private static AmazonSNS snsClient;
    private static String message; // Declare message as a class member

    public static void main(String[] args) {
        SpringApplication.run(AwsRekognitionAwsSnsApplication.class, args);

        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter AWS Access Key: ");
        String accessKey = scanner.nextLine();

        System.out.print("Enter AWS Secret Key: ");
        String secretKey = scanner.nextLine();

        System.out.print("Enter AWS S3 bucketName: ");
        String bucketName = scanner.nextLine();

        System.out.print("Enter the key of the image object in S3 bucket: ");
        String objectKey = scanner.nextLine();

        try {
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);

            // Initialize S3 client
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .build();

            // Download image from S3
            com.amazonaws.services.s3.model.S3Object s3Object = s3.getObject(bucketName, objectKey);
            S3ObjectInputStream inputStream = s3Object.getObjectContent();
            ByteBuffer imageBytes = ByteBuffer.wrap(inputStream.readAllBytes());

            // Initialize Rekognition client
            AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .build();

            // Detect labels in the image
            DetectLabelsRequest request = new DetectLabelsRequest()
                    .withImage(new Image().withBytes(imageBytes))
                    .withMaxLabels(10)
                    .withMinConfidence(75F);

            DetectLabelsResult result = rekognitionClient.detectLabels(request);

            // Prepare message for SNS
            StringBuilder messageBuilder = new StringBuilder("Detected labels:\n");
            List<Label> labels = result.getLabels();
            for (Label label : labels) {
                messageBuilder.append(label.getName()).append(": ").append(label.getConfidence()).append("\n");
            }
            message = messageBuilder.toString(); // Assign the message to the class member

            // Initialize SNS client
            snsClient = AmazonSNSClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .build();


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    String TOPIC_ARN = "";//enter topic arn here

    @GetMapping("/addSubscription/{email}")
    public String addSubscription(@PathVariable String email) {
        SubscribeRequest request = new SubscribeRequest(TOPIC_ARN, "email", email);
        snsClient.subscribe(request);
        return "Subscription request is pending. To confirm the subscription, check your email : " + email;
    }

    @GetMapping("/sendNotification")
    public String publishMessageToTopic() {
        PublishRequest publishRequest = new PublishRequest(TOPIC_ARN, message, "Rekognition Labels");
        snsClient.publish(publishRequest);
        return "Notification send successfully !!";
    }

}
