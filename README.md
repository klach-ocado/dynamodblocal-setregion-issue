Reported issue: https://github.com/awslabs/amazon-dynamodb-local-samples/issues/13

---

This project reproduces an exception in `DynamoDBLocal` when embedded version is used:

```
com.amazonaws.AmazonServiceException: Regions are not supported in LocalDynamoDB (Service: null; Status Code: 400; Error Code: InvalidAction; Request ID: null; Proxy: null)

	at com.amazonaws.services.dynamodbv2.local.embedded.DDBExceptionMappingInvocationHandler.handleDynamoDBLocalServiceException(DDBExceptionMappingInvocationHandler.java:50)
	at com.amazonaws.services.dynamodbv2.local.embedded.DDBExceptionMappingInvocationHandler.invoke(DDBExceptionMappingInvocationHandler.java:126)
	at jdk.proxy2/jdk.proxy2.$Proxy76.setRegion(Unknown Source)
	at com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker.setField(Worker.java:1262)
	at com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker.access$500(Worker.java:92)
	at com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker$Builder.build(Worker.java:1477)
	at com.amazonaws.services.dynamodbv2.streamsadapter.StreamsWorkerFactory.createDynamoDbStreamsWorker(StreamsWorkerFactory.java:165)
```

For comparison, when Jetty version (or a client configured for production use) is used, the following code:

```
// com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker.setField
    private static <S, T> void setField(final S source, final String field, final Consumer<T> t, T value) {
        try {
            t.accept(value);
        } catch (UnsupportedOperationException e) {
            LOG.debug("Exception thrown while trying to set " + field + ", indicating that "
                    + source.getClass().getSimpleName() + "is immutable.", e);
        }
    }
```

catches an exception thrown here:

```
// com.amazonaws.AmazonWebServiceClient.setRegion
    public void setRegion(Region region) throws IllegalArgumentException {
        checkMutability();
        // ...
    }

// com.amazonaws.AmazonWebServiceClient.checkMutability
    protected final void checkMutability() {
        if (isImmutable) {
            throw new UnsupportedOperationException(
                    "Client is immutable when created with the builder.");
        }
    }
```

... and no hacks have to be done in tests.

Since Spring Boot 3.2 requires Jetty 12 while `DynamoDBLocal` still uses Jetty 11, we need to use embedded version of the mock, or prepare even bigger hacks for `DynamoDBLocal` to work with Spring Boot 3.2.
