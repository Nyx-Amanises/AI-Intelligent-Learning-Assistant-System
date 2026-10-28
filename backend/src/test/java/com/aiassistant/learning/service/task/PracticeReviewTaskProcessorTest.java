package com.aiassistant.learning.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.PracticeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PracticeReviewTaskProcessorTest {

    private final PracticeService practices = mock(PracticeService.class);
    private final PracticeReviewTaskProcessor processor = new PracticeReviewTaskProcessor(practices, new ObjectMapper());

    @Test
    void neverReviewsAPracticeSessionOwnedBySomeoneElse() {
        when(practices.getPracticeDetail(7L, 99L)).thenThrow(new BusinessException(404, "练习记录不存在"));

        BusinessException exception = assertThrows(BusinessException.class, () -> processor.process(task()));

        assertEquals(404, exception.getCode());
        verify(practices, never()).reviewPendingShortAnswersNow(any());
    }

    @Test
    void checksOwnershipBeforeReviewing() {
        processor.process(task());

        InOrder order = inOrder(practices);
        order.verify(practices).getPracticeDetail(7L, 99L);
        order.verify(practices).reviewPendingShortAnswersNow(99L);
        order.verify(practices).getPracticeDetail(7L, 99L);
    }

    private AiTask task() {
        AiTask task = new AiTask();
        task.setId(1L);
        task.setUserId(7L);
        task.setTaskType("PRACTICE_REVIEW");
        task.setPayloadJson("{\"sessionId\":99}");
        return task;
    }
}
