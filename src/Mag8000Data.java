public final class Mag8000Data {

    private Mag8000Data() {
    }

    // File CSV được đặt ở project root của IntelliJ.
    public static final String REGISTER_MAP_FILE =
            "mag8000_register_map.csv";

    // Tên group trong CSV. Chỉ dùng để dễ gọi và tránh gõ sai chuỗi.
    public static final String GROUP_DEVICE = "DEVICE";
    public static final String GROUP_LIVE_FLOW = "LIVE_FLOW";
    public static final String GROUP_FLOW_DIAGNOSTIC = "FLOW_DIAGNOSTIC";
    public static final String GROUP_TOTALIZER = "TOTALIZER";
    public static final String GROUP_STATUS = "STATUS";
    public static final String GROUP_POWER = "POWER";
    public static final String GROUP_COMMUNICATION = "COMMUNICATION";
    public static final String GROUP_SENSOR_CONFIG = "SENSOR_CONFIG";
    public static final String GROUP_PULSE_OUTPUT = "PULSE_OUTPUT";
    public static final String GROUP_SERVICE = "SERVICE";
    public static final String GROUP_STATISTICS = "STATISTICS";
}
