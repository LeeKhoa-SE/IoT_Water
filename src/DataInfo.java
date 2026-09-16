public class DataInfo {
    private final String name;
    private final int siemensRegister;
    private final DataType type;
    private final int sizeBytes;
    private final String unit;
    private final AccessMode accessMode;
    private final String group;
    private final boolean defaultMonitor;
    private final String description;

    public DataInfo(
            String name,
            int siemensRegister,
            DataType type,
            int sizeBytes,
            String unit,
            AccessMode accessMode,
            String group,
            boolean defaultMonitor,
            String description
    ) {
        if (siemensRegister <= 0 || siemensRegister > 65536) {
            throw new IllegalArgumentException(
                    "Siemens register không hợp lệ: " + siemensRegister
            );
        }

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "sizeBytes phải > 0 cho " + name
            );
        }

        this.name = name;
        this.siemensRegister = siemensRegister;
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.unit = unit == null ? "" : unit;
        this.accessMode = accessMode;
        this.group = group == null ? "OTHER" : group;
        this.defaultMonitor = defaultMonitor;
        this.description = description == null ? "" : description;
    }

    public String getName() {
        return name;
    }

    // Số ghi trong manual Siemens, ví dụ 4:3001 -> 3001.
    public int getSiemensRegister() {
        return siemensRegister;
    }

    // Địa chỉ gửi trong Modbus = số register Siemens - 1.
    public int getAddress() {
        return siemensRegister - 1;
    }

    public DataType getType() {
        return type;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    // Modbus register luôn 16 bit = 2 byte.
    public int getRegisterCount() {
        return (sizeBytes + 1) / 2;
    }

    public int getEndAddressExclusive() {
        return getAddress() + getRegisterCount();
    }

    public String getUnit() {
        return unit;
    }

    public AccessMode getAccessMode() {
        return accessMode;
    }

    public String getGroup() {
        return group;
    }

    public boolean isDefaultMonitor() {
        return defaultMonitor;
    }

    public String getDescription() {
        return description;
    }

    public boolean isReadable() {
        return accessMode.isReadable();
    }

    public boolean isWritable() {
        return accessMode.isWritable();
    }
}
