package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.service.VectorStoreService.MaterialSegmentVector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QdrantVectorStoreServiceImplTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger deletionStatus = new AtomicInteger(200);
    private HttpServer server;
    private QdrantVectorStoreServiceImpl service;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/test-index", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isEmpty()) requests.add(mapper.readTree(body));
            boolean delete = exchange.getRequestURI().getPath().endsWith("/delete");
            byte[] response = ("GET".equals(exchange.getRequestMethod())
                    ? "{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":1}}}}}"
                    : "{\"status\":\"ok\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(delete ? deletionStatus.get() : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        QdrantProperties properties = new QdrantProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollectionName("test-index");
        service = new QdrantVectorStoreServiceImpl(properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void compensationUsesOwnerMaterialAndExactPointIdsPlusAttemptWhenProvided() {
        service.deleteSegmentVectors(7L, 42L, List.of(11L, 12L, 11L), "attempt-one");

        JsonNode conditions = requests.get(0).path("filter").path("must");
        assertEquals(4, conditions.size());
        assertEquals("userId", conditions.get(0).path("key").asText());
        assertEquals(7L, conditions.get(0).path("match").path("value").asLong());
        assertEquals("materialId", conditions.get(1).path("key").asText());
        assertEquals(42L, conditions.get(1).path("match").path("value").asLong());
        assertPointIds(conditions.get(2).path("has_id"), List.of(11L, 12L));
        assertEquals("embeddingAttemptId", conditions.get(3).path("key").asText());
        assertEquals("attempt-one", conditions.get(3).path("match").path("value").asText());
    }

    @Test
    void cleanupWhileHoldingMaterialLockCanInvalidateOldPendingPointsWithoutTouchingSkippedIds() {
        service.deleteSegmentVectors(7L, 42L, List.of(11L), null);

        JsonNode conditions = requests.get(0).path("filter").path("must");
        assertEquals(3, conditions.size());
        assertPointIds(conditions.get(2).path("has_id"), List.of(11L));
    }

    @Test
    void everyUpsertCarriesItsAttemptIdForLaterCompensation() {
        service.upsertMaterialSegments(List.of(new MaterialSegmentVector(
                11L, 7L, 42L, 1, 1, "标题", "正文", null, "embedding-model", "attempt-one", List.of(0.2D))));

        JsonNode point = requests.get(0).path("points").get(0);
        assertEquals(11L, point.path("id").asLong());
        assertEquals("attempt-one", point.path("payload").path("embeddingAttemptId").asText());
        assertEquals(7L, point.path("payload").path("userId").asLong());
    }

    @Test
    void missingCollectionIsAlreadyCleanButOtherFailuresAreReported() {
        deletionStatus.set(404);
        service.deleteSegmentVectors(7L, 42L, List.of(11L), "attempt-one");
        deletionStatus.set(503);

        assertTrue(assertThrows(BusinessException.class,
                () -> service.deleteSegmentVectors(7L, 42L, List.of(11L), "attempt-one"))
                .getMessage().contains("清理本次向量化数据失败"));
    }

    @Test
    void emptyIdListNeverSendsABroadDeleteRequest() {
        service.deleteSegmentVectors(7L, 42L, List.of(), null);

        assertTrue(requests.isEmpty());
    }

    private void assertPointIds(JsonNode actual, List<Long> expected) {
        assertTrue(actual.isArray());
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertTrue(actual.get(index).isIntegralNumber());
            assertEquals(expected.get(index).longValue(), actual.get(index).longValue());
        }
    }
}
