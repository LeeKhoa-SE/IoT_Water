public class Mag8000Data {

    /*
     * Siemens register trong tài liệu:
     *
     * 4:3001
     *
     * Raw Modbus address:
     *
     * 3001 - 1 = 3000
     */


    // ============================================================
    // DEVICE
    // ============================================================

    public static final DataInfo PRODUCT_ID =
            new DataInfo(
                    "Product ID",
                    79,
                    DataType.UINT16,
                    ""
            );


    // ============================================================
    // FLOW
    // ============================================================

    public static final DataInfo VELOCITY =
            new DataInfo(
                    "Velocity",
                    3000,
                    DataType.FLOAT32,
                    "mm/s"
            );

    public static final DataInfo FLOW_RATE =
            new DataInfo(
                    "Flow Rate",
                    3002,
                    DataType.FLOAT32,
                    ""
            );

    public static final DataInfo FLOW_PERCENT =
            new DataInfo(
                    "Flow Percentage",
                    3012,
                    DataType.FLOAT32,
                    "%"
            );


    // ============================================================
    // TOTALIZERS
    // ============================================================

    public static final DataInfo TOTALIZER_1 =
            new DataInfo(
                    "Totalizer 1",
                    3017,
                    DataType.TOTALTYPE,
                    "m3"
            );

    public static final DataInfo TOTALIZER_2 =
            new DataInfo(
                    "Totalizer 2",
                    3021,
                    DataType.TOTALTYPE,
                    "m3"
            );

    public static final DataInfo TOTALIZER_3 =
            new DataInfo(
                    "Totalizer 3",
                    3025,
                    DataType.TOTALTYPE,
                    "m3"
            );


    // ============================================================
    // POWER
    // ============================================================

    public static final DataInfo BATTERY_CAPACITY =
            new DataInfo(
                    "Battery Capacity",
                    3030,
                    DataType.UINT16,
                    "%"
            );

    public static final DataInfo POWER_STATUS =
            new DataInfo(
                    "Power Status",
                    3031,
                    DataType.UINT16,
                    ""
            );


    // ============================================================
    // DEVICE STATUS
    // ============================================================

    public static final DataInfo TEMPERATURE =
            new DataInfo(
                    "Transmitter Temperature",
                    3042,
                    DataType.FLOAT32,
                    "°C"
            );

    public static final DataInfo[] IMPORTANT_REGISTERS = {

            PRODUCT_ID,

            VELOCITY,
            FLOW_RATE,
            FLOW_PERCENT,

            TOTALIZER_1,
            TOTALIZER_2,
            TOTALIZER_3,

            BATTERY_CAPACITY,
            POWER_STATUS,

            TEMPERATURE
    };
}