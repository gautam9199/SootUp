package transformer.helpers;

public class Stats {
  private String className;
  private Double normalizedScore;
  private Double weightedScore;
  private String errorMessage;
  private long b2s;
  private long s2b;
  private double b2sMemory;
  private double s2bMemory;

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public Double getNormalizedScore() {
    return normalizedScore;
  }

  public void setNormalizedScore(Double normalizedScore) {
    this.normalizedScore = normalizedScore;
  }

  public Double getWeightedScore() {
    return weightedScore;
  }

  public void setWeightedScore(Double weightedScore) {
    this.weightedScore = weightedScore;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public long getS2b() {
    return s2b;
  }

  public void setS2b(long s2b) {
    this.s2b = s2b;
  }

  public long getB2s() {
    return b2s;
  }

  public void setB2s(long b2s) {
    this.b2s = b2s;
  }

  public double getB2sMemory() {
    return b2sMemory;
  }

  public void setB2sMemory(double b2sMemory) {
    this.b2sMemory = b2sMemory;
  }

  public double getS2bMemory() {
    return s2bMemory;
  }

  public void setS2bMemory(double s2bMemory) {
    this.s2bMemory = s2bMemory;
  }
}
