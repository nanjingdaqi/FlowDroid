package daqi;

public class SourceSinkInfo {

    public static int maxSourcePerTime = -1;
    public static int maxSinkPerTime = -1;
    public static boolean force = false;
    public static boolean applicationSingle = false;

    public int sourceCount, sinkCount;

    public SourceSinkInfo(int sourceCount, int sinkCount) {
        this.sourceCount = sourceCount;
        this.sinkCount = sinkCount;
    }
}
