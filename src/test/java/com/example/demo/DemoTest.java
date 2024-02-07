package com.example.demo;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import com.amazonaws.services.dynamodbv2.local.server.LocalDynamoDBRequestHandler;
import com.amazonaws.services.dynamodbv2.local.server.LocalDynamoDBServerHandler;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.StreamSpecification;
import com.amazonaws.services.dynamodbv2.model.StreamViewType;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import com.amazonaws.services.dynamodbv2.streamsadapter.AmazonDynamoDBStreamsAdapterClient;
import com.amazonaws.services.dynamodbv2.streamsadapter.StreamsWorkerFactory;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;

class DemoTest {

    // currently throws: com.amazonaws.AmazonServiceException: Regions are not supported in LocalDynamoDB
    @Test
    void dynamoDBEmbedded() {
        AmazonDynamoDB amazonDynamoDB = DynamoDBEmbedded.create().amazonDynamoDB();

        mainExecution(amazonDynamoDB);
    }

    @SneakyThrows
    @Test
    void dynamoDBJetty() {
        var dynamoDBProxyServer = new DynamoDBProxyServer(8080,
                new LocalDynamoDBServerHandler(new LocalDynamoDBRequestHandler(0, true, null, false, false), null));
        dynamoDBProxyServer.start();
        AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("accessKey", "secretKey")))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration( "http://localhost:8080", "us-east-1"))
                .build();

        mainExecution(amazonDynamoDB);

        dynamoDBProxyServer.stop();
    }

    private void mainExecution(AmazonDynamoDB amazonDynamoDB) {
        DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(amazonDynamoDB);
        var createTableRequest = dynamoDBMapper.generateCreateTableRequest(SomeTable.class)
                .withBillingMode(BillingMode.PAY_PER_REQUEST);
        amazonDynamoDB.createTable(createTableRequest);
        enableDynamoDBStream(amazonDynamoDB);
        var describeTableResult = amazonDynamoDB.describeTable("some-table");

        StreamsWorkerFactory.createDynamoDbStreamsWorker(mock(IRecordProcessorFactory.class),
                getKinesisClientLibConfiguration(describeTableResult.getTable().getLatestStreamArn()),
                mock(AmazonDynamoDBStreamsAdapterClient.class),
                amazonDynamoDB,
                mock(AmazonCloudWatch.class));
    }

    private void enableDynamoDBStream(AmazonDynamoDB amazonDynamoDB) {
        var streamSpecification = new StreamSpecification();
        streamSpecification.setStreamEnabled(true);
        streamSpecification.setStreamViewType(StreamViewType.NEW_AND_OLD_IMAGES);
        var updateTableRequest = new UpdateTableRequest();
        updateTableRequest.setStreamSpecification(streamSpecification);
        updateTableRequest.setTableName("some-table");
        amazonDynamoDB.updateTable(updateTableRequest);
    }

    private KinesisClientLibConfiguration getKinesisClientLibConfiguration(String streamArn) {
        return new KinesisClientLibConfiguration(
                "application-name",
                streamArn,
                new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()),
                UUID.randomUUID().toString())
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withRegionName("us-east-1");
    }
}
