package com.devops.agent.service;

import com.devops.agent.model.LogSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private CloudWatchLogsClient cloudWatchLogsClient;

    private LogService logService;

    @BeforeEach
    void setUp() {
        logService = new LogService(cloudWatchLogsClient);
    }

    @Test
    void testGetAllLogGroups_Success() {
        // Arrange
        LogGroup logGroup = LogGroup.builder()
                .logGroupName("/aws/lambda/test-function")
                .build();
        DescribeLogGroupsResponse response = DescribeLogGroupsResponse.builder()
                .logGroups(List.of(logGroup))
                .build();

        when(cloudWatchLogsClient.describeLogGroups()).thenReturn(response);

        // Act
        List<String> result = logService.getAllLogGroups();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("/aws/lambda/test-function", result.get(0));
        verify(cloudWatchLogsClient, times(1)).describeLogGroups();
    }

    @Test
    void testGetAllLogGroups_Exception() {
        // Arrange
        when(cloudWatchLogsClient.describeLogGroups())
                .thenThrow(CloudWatchLogsException.builder().message("AWS Error").build());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> logService.getAllLogGroups());
        assertTrue(exception.getMessage().contains("Failed to fetch log groups"));
    }

    @Test
    void testGetLogStreams_Success() {
        // Arrange
        LogStream logStream = LogStream.builder()
                .logStreamName("2024/01/15/stream-1")
                .build();
        DescribeLogStreamsResponse response = DescribeLogStreamsResponse.builder()
                .logStreams(List.of(logStream))
                .build();

        when(cloudWatchLogsClient.describeLogStreams(any(DescribeLogStreamsRequest.class)))
                .thenReturn(response);

        // Act
        List<String> result = logService.getLogStreams("/aws/lambda/test-function");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("2024/01/15/stream-1", result.get(0));
        verify(cloudWatchLogsClient, times(1)).describeLogStreams(any(DescribeLogStreamsRequest.class));
    }

    @Test
    void testGetLogSummary_Success() {
        // Arrange
        OutputLogEvent errorEvent = OutputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message("ERROR: Something went wrong")
                .build();
        
        OutputLogEvent infoEvent = OutputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message("INFO: Operation successful")
                .build();

        GetLogEventsResponse response = GetLogEventsResponse.builder()
                .events(List.of(errorEvent, infoEvent))
                .build();

        when(cloudWatchLogsClient.getLogEvents(any(GetLogEventsRequest.class))).thenReturn(response);

        // Act
        LogSummaryResponse result = logService.getLogSummary(
                "/aws/lambda/test-function", 
                "2024/01/15/stream-1", 
                24);

        // Assert
        assertNotNull(result);
        assertEquals("/aws/lambda/test-function", result.getLogGroupName());
        assertEquals("2024/01/15/stream-1", result.getLogStreamName());
        assertEquals(2, result.getTotalEvents());
        assertNotNull(result.getStatistics());
        assertEquals(1, result.getStatistics().getErrorCount());
        assertEquals(1, result.getStatistics().getInfoCount());
        assertEquals(2, result.getEvents().size());
        verify(cloudWatchLogsClient, times(1)).getLogEvents(any(GetLogEventsRequest.class));
    }

    @Test
    void testGetLogSummaryForGroup_Success() {
        // Arrange
        LogStream logStream = LogStream.builder()
                .logStreamName("2024/01/15/stream-1")
                .build();
        DescribeLogStreamsResponse streamsResponse = DescribeLogStreamsResponse.builder()
                .logStreams(List.of(logStream))
                .build();

        OutputLogEvent logEvent = OutputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message("INFO: Test message")
                .build();
        GetLogEventsResponse eventsResponse = GetLogEventsResponse.builder()
                .events(List.of(logEvent))
                .build();

        when(cloudWatchLogsClient.describeLogStreams(any(DescribeLogStreamsRequest.class)))
                .thenReturn(streamsResponse);
        when(cloudWatchLogsClient.getLogEvents(any(GetLogEventsRequest.class)))
                .thenReturn(eventsResponse);

        // Act
        LogSummaryResponse result = logService.getLogSummaryForGroup("/aws/lambda/test-function", 24);

        // Assert
        assertNotNull(result);
        assertEquals("/aws/lambda/test-function", result.getLogGroupName());
        assertEquals(1, result.getTotalEvents());
    }

    @Test
    void testGetLogSummaryForGroup_NoStreamsFound() {
        // Arrange
        DescribeLogStreamsResponse response = DescribeLogStreamsResponse.builder()
                .logStreams(Collections.emptyList())
                .build();

        when(cloudWatchLogsClient.describeLogStreams(any(DescribeLogStreamsRequest.class)))
                .thenReturn(response);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> logService.getLogSummaryForGroup("/aws/lambda/test-function", 24));
        assertTrue(exception.getMessage().contains("No log streams found"));
    }
}
