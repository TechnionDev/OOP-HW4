package solution;

import provided.StoryTestException;

import java.util.List;

public class StoryTestExceptionImpl extends StoryTestException {

    private int numOfFails;
    private final String sentence;
    private final String storyExpected;
    private final String testResult;

    public StoryTestExceptionImpl(String sentence, String storyExpected, String testResult) {
        this.numOfFails = 0;
        this.sentence = sentence;
        this.storyExpected = storyExpected;
        this.testResult = testResult;
    }

    /**
     * Returns a string representing the sentence
     * of the first Then sentence that failed
     */
    @Override
    public String getSentance() {
        return this.sentence;
    }

    /**
     * Returns a string representing the expected value from the story
     * of the first Then sentence that failed.
     */
    @Override
    public String getStoryExpected() {
        return this.storyExpected;
    }

    /**
     * Returns a string representing the actual value.
     * of the first Then sentence that failed.
     */
    @Override
    public String getTestResult() {
        return this.testResult;
    }

    /**
     * Returns an int representing the number of Then sentences that failed.
     */
    @Override
    public int getNumFail() {
        return this.numOfFails;
    }

    public void setNumOfFails(int failures) {
        this.numOfFails = failures;
    }
}
