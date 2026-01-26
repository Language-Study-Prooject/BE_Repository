package com.mzc.secondproject.serverless.domain.opic.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * OPIc 질문 마스터 데이터
 */
@DynamoDbBean
public class OPIcQuestion {
	
	private String pk;                    // QUESTION#questionId
	private String sk;                    // METADATA
	private String gsi1pk;                // TOPIC#DESCRIPTION  (질문 유형 - 대주제)
	private String gsi1sk;                // SUBTOPIC#HOMES     (질문 소재 - 소주제)
	
	private String questionId;
	private String topic;                 // DESCRIPTION, HABIT, PAST_EXPERIENCE ...
	private String subTopic;              // HOMES, BANKS, MUSIC ...
	private String level;                 // 난이도 (IM1, IM2, IM3, IH, AL)
	private String questionText;          // 질문 텍스트 (영어)
	private String questionTextKo;        // 질문 텍스트 (한국어, 참고용)
	private String audioS3Key;            // 질문 음성 S3 키 (Polly 캐시)
	private String tips;                  // 답변 팁
	private int orderInSet;               // 콤보 세트 내 순서 (1, 2, 3)
	private boolean isActive;             // 활성화 여부
	
	@DynamoDbPartitionKey
	@DynamoDbAttribute("PK")
	public String getPk() {
		return pk;
	}
	
	public void setPk(String pk) {
		this.pk = pk;
	}
	
	@DynamoDbSortKey
	@DynamoDbAttribute("SK")
	public String getSk() {
		return sk;
	}
	
	public void setSk(String sk) {
		this.sk = sk;
	}
	
	@DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
	@DynamoDbAttribute("GSI1PK")
	public String getGsi1pk() {
		return gsi1pk;
	}
	
	public void setGsi1pk(String gsi1pk) {
		this.gsi1pk = gsi1pk;
	}
	
	@DynamoDbSecondarySortKey(indexNames = "GSI1")
	@DynamoDbAttribute("GSI1SK")
	public String getGsi1sk() {
		return gsi1sk;
	}
	
	public void setGsi1sk(String gsi1sk) {
		this.gsi1sk = gsi1sk;
	}
	
	@DynamoDbAttribute("questionId")
	public String getQuestionId() {
		return questionId;
	}
	
	public void setQuestionId(String id) {
		this.questionId = id;
	}
	
	@DynamoDbAttribute("topic")
	public String getTopic() {
		return topic;
	}
	
	public void setTopic(String topic) {
		this.topic = topic;
	}
	
	@DynamoDbAttribute("subTopic")
	public String getSubTopic() {
		return subTopic;
	}
	
	public void setSubTopic(String subTopic) {
		this.subTopic = subTopic;
	}
	
	@DynamoDbAttribute("level")
	public String getLevel() {
		return level;
	}
	
	public void setLevel(String level) {
		this.level = level;
	}
	
	@DynamoDbAttribute("questionText")
	public String getQuestionText() {
		return questionText;
	}
	
	public void setQuestionText(String text) {
		this.questionText = text;
	}
	
	@DynamoDbAttribute("questionTextKo")
	public String getQuestionTextKo() {
		return questionTextKo;
	}
	
	public void setQuestionTextKo(String text) {
		this.questionTextKo = text;
	}
	
	@DynamoDbAttribute("audioS3Key")
	public String getAudioS3Key() {
		return audioS3Key;
	}
	
	public void setAudioS3Key(String key) {
		this.audioS3Key = key;
	}
	
	@DynamoDbAttribute("tips")
	public String getTips() {
		return tips;
	}
	
	public void setTips(String tips) {
		this.tips = tips;
	}
	
	@DynamoDbAttribute("orderInSet")
	public int getOrderInSet() {
		return orderInSet;
	}
	
	public void setOrderInSet(int order) {
		this.orderInSet = order;
	}
	
	@DynamoDbAttribute("isActive")
	public boolean isActive() {
		return isActive;
	}
	
	public void setActive(boolean active) {
		this.isActive = active;
	}
}
