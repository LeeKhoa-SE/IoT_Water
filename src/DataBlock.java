import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataBlock {
    private final String name;
    private final int startAddress;
    private final int registerCount;
    private final List<DataInfo> dataPoints;

    public DataBlock(String name, List<DataInfo> dataPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            throw new IllegalArgumentException("DataBlock không được rỗng.");
        }

        List<DataInfo> sorted = new ArrayList<>(dataPoints);
        sorted.sort(Comparator.comparingInt(DataInfo::getAddress));

        int start = sorted.get(0).getAddress();
        int endExclusive = start;

        for (DataInfo info : sorted) {
            if (!info.isReadable()) {
                throw new IllegalArgumentException(
                        info.getName() + " là WRITE_ONLY nên không thể nằm trong read block."
                );
            }

            if (info.getAddress() > endExclusive) {
                throw new IllegalArgumentException(
                        "Block " + name + " có khoảng trống giữa register "
                                + endExclusive + " và " + info.getAddress()
                );
            }

            endExclusive = Math.max(
                    endExclusive,
                    info.getEndAddressExclusive()
            );
        }

        int count = endExclusive - start;

        if (count > 125) {
            throw new IllegalArgumentException(
                    "Block " + name + " vượt quá giới hạn 125 Modbus registers."
            );
        }

        this.name = name;
        this.startAddress = start;
        this.registerCount = count;
        this.dataPoints = Collections.unmodifiableList(sorted);
    }

    public String getName() {
        return name;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public int getRegisterCount() {
        return registerCount;
    }

    public List<DataInfo> getDataPoints() {
        return dataPoints;
    }
}
