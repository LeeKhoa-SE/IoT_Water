public class DataInfo {
    private final String name;
    private final int address;
    private final DataType type;
    private final String unit;

    public DataInfo(
            String name,
            int address,
            DataType type,
            String unit
    ) {
        this.name = name;
        this.address = address;
        this.type = type;
        this.unit = unit;
    }

    public String getName() {
        return name;
    }

    public int getAddress() {
        return address;
    }

    public DataType getType() {
        return type;
    }

    public String getUnit() {
        return unit;
    }
}
