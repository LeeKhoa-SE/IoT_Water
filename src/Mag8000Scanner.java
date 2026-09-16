public final class Mag8000Scanner {

    private Mag8000Scanner() {
    }

    // ============================================================
    // 1) SCAN CÁC PARAMETER ĐÃ CÓ TRONG REGISTER MAP
    // ============================================================
    // Đây là chế độ nên dùng khi muốn "đọc tất cả thông số đã biết".
    // Không brute-force địa chỉ; datatype / size / name / unit lấy từ CSV.
    public static void scanDocumentedParameters(
            Mag8000Connect mag,
            Mag8000RegisterMap registerMap
    ) {

        System.out.println();
        System.out.println("============================================================");
        System.out.println(" MAG-8000 DOCUMENTED PARAMETER SCAN");
        System.out.println("============================================================");

        int ok = 0;
        int errors = 0;
        int writeOnly = 0;

        for (DataInfo info : registerMap.getAll()) {

            if (!info.isReadable()) {
                printWriteOnly(info);
                writeOnly++;
                continue;
            }

            try {
                Object value = mag.readSilent(info);
                printKnown(info, value);
                ok++;

            } catch (Exception e) {
                System.out.println(
                        "[KNOWN-ERROR] "
                                + registerLabel(info)
                                + " | " + info.getName()
                                + " | " + e.getMessage()
                );
                errors++;
            }
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("Read OK    : " + ok);
        System.out.println("Read errors: " + errors);
        System.out.println("Write only : " + writeOnly);
        System.out.println("============================================================");
    }


    // ============================================================
    // 2) SMART RANGE SCAN
    // ============================================================
    // Nếu địa chỉ có metadata -> đọc đúng datatype/size và in tên.
    // Nếu không có metadata nhưng meter vẫn phản hồi -> chỉ in RAW HEX.
    // Không giả định UNKNOWN là UINT16 vì có thể đó chỉ là một phần của
    // datatype khác hoặc register undocumented/reserved.
    public static void scanRange(
            Mag8000Connect mag,
            Mag8000RegisterMap registerMap,
            int startAddress,
            int endAddress
    ) {

        validateRange(startAddress, endAddress);

        System.out.println();
        System.out.println("============================================================");
        System.out.println(" MAG-8000 SMART REGISTER SCAN");
        System.out.println(
                " Raw range: " + startAddress + " -> " + endAddress
        );
        System.out.println("============================================================");

        int known = 0;
        int unknown = 0;
        int errors = 0;

        int address = startAddress;

        while (address <= endAddress) {

            DataInfo info =
                    registerMap.getByRawStartAddress(address);

            if (info != null) {

                if (!info.isReadable()) {
                    printWriteOnly(info);
                    address += info.getRegisterCount();
                    continue;
                }

                try {
                    Object value = mag.readSilent(info);
                    printKnown(info, value);
                    known++;

                } catch (Exception e) {
                    System.out.println(
                            "[KNOWN-ERROR] "
                                    + registerLabel(info)
                                    + " | " + info.getName()
                                    + " | " + e.getMessage()
                    );
                    errors++;
                }

                // Parameter nhiều register chỉ in một lần.
                address += info.getRegisterCount();
                continue;
            }

            // Nếu range bắt đầu giữa một parameter nhiều register,
            // bỏ qua phần continuation để không báo nhầm UNKNOWN.
            DataInfo containing =
                    registerMap.findContainingRawAddress(address);

            if (containing != null) {
                address = containing.getEndAddressExclusive();
                continue;
            }

            try {
                byte[] raw =
                        mag.readRegistersSilent(address, 1);

                System.out.println(
                        "[UNKNOWN] raw=" + address
                                + " | Siemens=4:"
                                + String.format("%04d", address + 1)
                                + " | HEX=" + mag.bytesToHex(raw)
                                + " | no metadata in CSV"
                );

                unknown++;

            } catch (Exception ignored) {
                // Không phản hồi / illegal address -> bỏ qua.
            }

            address++;
        }

        System.out.println("------------------------------------------------------------");
        System.out.println("Known parameters : " + known);
        System.out.println("Unknown responses: " + unknown);
        System.out.println("Known read errors: " + errors);
        System.out.println("============================================================");
    }


    // ============================================================
    // 3) FULL ADDRESS SPACE SCAN
    // ============================================================
    // Chỉ dùng khi thật sự cần khám phá/debug/reverse-engineer.
    public static void scanFullAddressSpace(
            Mag8000Connect mag,
            Mag8000RegisterMap registerMap
    ) {

        System.out.println();
        System.out.println(
                "[WARNING] Full raw scan 0..65535 có thể mất rất lâu "
                        + "và tạo rất nhiều Modbus request."
        );

        scanRange(
                mag,
                registerMap,
                0,
                0xFFFF
        );
    }


    private static void printKnown(
            DataInfo info,
            Object value
    ) {

        StringBuilder line = new StringBuilder();

        line.append("[KNOWN] ")
                .append(registerLabel(info))
                .append(" | raw=")
                .append(info.getAddress())
                .append(" | ")
                .append(info.getName())
                .append(" | type=")
                .append(info.getType())
                .append(" | value=")
                .append(value);

        if (!info.getUnit().isEmpty()) {
            line.append(" ").append(info.getUnit());
        }

        line.append(" | access=")
                .append(info.getAccessMode())
                .append(" | group=")
                .append(info.getGroup());

        System.out.println(line);
    }


    private static void printWriteOnly(DataInfo info) {
        System.out.println(
                "[KNOWN-WRITE-ONLY] "
                        + registerLabel(info)
                        + " | raw=" + info.getAddress()
                        + " | " + info.getName()
                        + " | type=" + info.getType()
                        + " | access=" + info.getAccessMode()
        );
    }


    private static String registerLabel(DataInfo info) {
        return "4:" + String.format(
                "%04d",
                info.getSiemensRegister()
        );
    }


    private static void validateRange(
            int startAddress,
            int endAddress
    ) {
        if (startAddress < 0
                || endAddress > 0xFFFF
                || startAddress > endAddress) {

            throw new IllegalArgumentException(
                    "Khoảng raw address scan không hợp lệ."
            );
        }
    }
}
