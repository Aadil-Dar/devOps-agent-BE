package com.devops.agent.config;

import com.devops.agent.model.ProjectConfiguration;
import com.devops.agent.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * Configuration for DynamoDB client and table
 */
@Configuration
public class DynamoDbConfig {

    @Value("${aws.region:eu-west-1}")
    private String awsRegion;

    @Value("${aws.dynamodb.table-name:devops-projects}")
    private String tableName;

    @Value("${aws.dynamodb.users-table-name:devops-users}")
    private String usersTableName;

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder =  DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<ProjectConfiguration> projectConfigurationTable(
            DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName,
                TableSchema.fromBean(ProjectConfiguration.class));
    }

    @Bean
    public DynamoDbTable<User> userTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(usersTableName,
                TableSchema.fromBean(User.class));
    }
}


    @Bean
    public DynamoDbTable<HealthInsightRecord> healthInsightTable(
            DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(healthInsightsTableName,
                TableSchema.fromBean(HealthInsightRecord.class));
    }
}
