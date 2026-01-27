//package com.devops.agent.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
//import software.amazon.awssdk.services.dynamodb.model.*;
//
//import jakarta.annotation.PostConstruct;
//
//@Slf4j
//@Service
//public class DynamoDbInitService {
//
//    @Autowired
//    private DynamoDbClient dynamoDbClient;
//
//    @Value("${aws.dynamodb.table-name:devops-projects}")
//    private String projectsTableName;
//
//    @Value("${aws.dynamodb.users-table-name:devops-users}")
//    private String usersTableName;
//
//    @PostConstruct
//    public void init() {
//        createUsersTableIfNotExists();
////        createProjectsTableIfNotExists();
//    }
//
//    private void createUsersTableIfNotExists() {
//        try {
//            // Check if table exists
//            try {
//                dynamoDbClient.describeTable(DescribeTableRequest.builder()
//                        .tableName(usersTableName)
//                        .build());
//                log.info("Users table '{}' already exists", usersTableName);
//                return;
//            } catch (ResourceNotFoundException e) {
//                // Table doesn't exist, create it
//                log.info("Creating users table '{}'", usersTableName);
//            }
//
//            CreateTableRequest request = CreateTableRequest.builder()
//                    .tableName(usersTableName)
//                    .keySchema(KeySchemaElement.builder()
//                            .attributeName("id")
//                            .keyType(KeyType.HASH)
//                            .build())
//                    .attributeDefinitions(
//                            AttributeDefinition.builder()
//                                    .attributeName("id")
//                                    .attributeType(ScalarAttributeType.S)
//                                    .build())
//                    .billingMode(BillingMode.PAY_PER_REQUEST)
//                    .build();
//
//            dynamoDbClient.createTable(request);
//            dynamoDbClient.waiter()
//                    .waitUntilTableExists(
//                            DescribeTableRequest.builder()
//                                    .tableName(usersTableName)
//                                    .build()
//                    );
//
//            log.info("Users table '{}' is ACTIVE", usersTableName);
//            log.info("Successfully created users table '{}'", usersTableName);
//
//        } catch (Exception e) {
//            log.error("Error creating users table: {}", e.getMessage(), e);
//        }
//    }
//
////    private void createProjectsTableIfNotExists() {
////        try {
////            // Check if table exists
////            try {
////                dynamoDbClient.describeTable(DescribeTableRequest.builder()
////                        .tableName(projectsTableName)
////                        .build());
////                log.info("Projects table '{}' already exists", projectsTableName);
////                return;
////            } catch (ResourceNotFoundException e) {
////                // Table doesn't exist, create it
////                log.info("Creating projects table '{}'", projectsTableName);
////            }
////
////            CreateTableRequest request = CreateTableRequest.builder()
////                    .tableName(projectsTableName)
////                    .keySchema(KeySchemaElement.builder()
////                            .attributeName("projectId")
////                            .keyType(KeyType.HASH)
////                            .build())
////                    .attributeDefinitions(
////                            AttributeDefinition.builder()
////                                    .attributeName("projectId")
////                                    .attributeType(ScalarAttributeType.S)
////                                    .build())
////                    .billingMode(BillingMode.PAY_PER_REQUEST)
////                    .build();
////
////            dynamoDbClient.createTable(request);
////            log.info("Successfully created projects table '{}'", projectsTableName);
////
////        } catch (Exception e) {
////            log.error("Error creating projects table: {}", e.getMessage(), e);
////        }
////    }
//}
//
