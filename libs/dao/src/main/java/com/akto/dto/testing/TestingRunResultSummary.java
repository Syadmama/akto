package com.akto.dto.testing;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

public class TestingRunResultSummary {
    
    public static final String START_TIMESTAMP = "startTimestamp";    
    public static final String END_TIMESTAMP = "endTimestamp";    
    public static final String COUNT_ISSUES = "countIssues";    
    public static final String TOTAL_APIS = "totalApis";    
    public static final String TESTING_RUN_ID = "testingRunId";    
    public static final String STATE = "state";    

    private ObjectId id;
    private int startTimestamp;
    private int endTimestamp;
    private Map<String, Integer> countIssues;
    private int totalApis;
    private ObjectId testingRunId;
    @BsonIgnore
    private String testingRunHexId;

    private TestingRun.State state; 

    @BsonIgnore
    private String hexId;


    public TestingRunResultSummary() {
    }

    public TestingRunResultSummary(int startTimestamp, int endTimestamp, Map<String,Integer> countIssues, int totalApis, ObjectId testingRunId, String testingRunHexId) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.countIssues = countIssues;
        this.totalApis = totalApis;
        this.testingRunId = testingRunId;
        this.testingRunHexId = testingRunHexId;
        this.state = TestingRun.State.RUNNING;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getStartTimestamp() {
        return this.startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return this.endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public Map<String,Integer> getCountIssues() {
        return this.countIssues;
    }

    public void setCountIssues(Map<String,Integer> countIssues) {
        this.countIssues = countIssues;
    }

    public int getTotalApis() {
        return this.totalApis;
    }

    public void setTotalApis(int totalApis) {
        this.totalApis = totalApis;
    }

    public ObjectId getTestingRunId() {
        return this.testingRunId;
    }

    public void setTestingRunId(ObjectId testingRunId) {
        this.testingRunId = testingRunId;
    }

    public String getTestingRunHexId() {
        return this.testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public TestingRun.State getState() {
        return this.state;
    }

    public void setState(TestingRun.State state) {
        this.state = state;
    }

    public String getHexId() {
        return this.id.toHexString();
    }

    @Override
    public String toString() {
        return "{" +
            " startTimestamp='" + getStartTimestamp() + "'" +
            ", endTimestamp='" + getEndTimestamp() + "'" +
            ", countIssues='" + getCountIssues() + "'" +
            ", totalApis='" + getTotalApis() + "'" +
            ", testingRunId='" + getTestingRunId() + "'" +
            ", testingRunHexId='" + getTestingRunHexId() + "'" +
            ", state='" + getState() + "'" +
            "}";
    }
}
