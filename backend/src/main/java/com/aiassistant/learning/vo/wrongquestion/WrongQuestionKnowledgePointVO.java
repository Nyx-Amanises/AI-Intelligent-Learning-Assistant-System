package com.aiassistant.learning.vo.wrongquestion;

/** 当前筛选范围内的完整知识点标签及错题记录数量。 */
public record WrongQuestionKnowledgePointVO(String knowledgePoint, long wrongCount) {
}
