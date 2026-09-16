import java.util.List;

public class Main {

    private enum Mode {
        NORMAL,
        SCAN_DOCUMENTED,
        SCAN_RANGE,
        FULL_SCAN
    }

    // ============================================================
    // CHỌN CHẾ ĐỘ CHẠY Ở ĐÂY
    // ============================================================
    private static final Mode MODE = Mode.SCAN_DOCUMENTED;

    // Chỉ dùng khi MODE = SCAN_RANGE.
    // Đây là RAW Modbus address, không phải số 4:xxxx trong manual.
    private static final int SCAN_START = 3000;
    private static final int SCAN_END = 3050;


    public static void main(String[] args) {

        // Load metadata trước. Nếu CSV sai thì dừng ngay trước khi connect.
        Mag8000RegisterMap registerMap =
                Mag8000RegisterMap.loadDefault();

        Mag8000Connect mag =
                new Mag8000Connect();

        try {
            mag.connect();

            switch (MODE) {

                case NORMAL:
                    runNormalMode(
                            mag,
                            registerMap
                    );
                    break;

                case SCAN_DOCUMENTED:
                    // Đọc tất cả parameter có trong CSV bằng đúng datatype.
                    Mag8000Scanner.scanDocumentedParameters(
                            mag,
                            registerMap
                    );
                    break;

                case SCAN_RANGE:
                    // Debug một vùng raw address. Known -> decode theo CSV.
                    // Unknown -> chỉ hiện HEX, không đoán datatype.
                    Mag8000Scanner.scanRange(
                            mag,
                            registerMap,
                            SCAN_START,
                            SCAN_END
                    );
                    break;

                case FULL_SCAN:
                    // Chỉ bật khi thật sự cần khám phá toàn address space.
                    Mag8000Scanner.scanFullAddressSpace(
                            mag,
                            registerMap
                    );
                    break;
            }

        } finally {
            mag.close();
        }
    }


    private static void runNormalMode(
            Mag8000Connect mag,
            Mag8000RegisterMap registerMap
    ) {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("            MAG-8000 NORMAL / IoT MODE");
        System.out.println("============================================================");

        /*
         * Không còn khai báo từng DataInfo trong Java.
         * CSV có cột default_monitor=true/false.
         * Chỉ những parameter = true mới được đưa vào production polling.
         *
         * Các parameter liên tiếp trong cùng group được tự gộp thành block.
         * Có khoảng trống -> tự tách block để không đọc vùng không hợp lệ.
         */
        List<DataBlock> blocks =
                registerMap.buildDefaultMonitoringBlocks();

        for (DataBlock block : blocks) {
            mag.printBlock(block);
        }

        System.out.println();
        System.out.println("Total Modbus read blocks: " + blocks.size());
        System.out.println("============================================================");

        /*
         * Nếu chỉ muốn đọc riêng một group khi cần:
         *
         * for (DataBlock block : registerMap.buildBlocksForGroup(
         *         Mag8000Data.GROUP_FLOW_DIAGNOSTIC)) {
         *     mag.printBlock(block);
         * }
         */
    }
}
