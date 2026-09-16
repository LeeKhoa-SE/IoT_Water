import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class Mag8000RegisterMap {

    private static final int MAX_READ_REGISTERS = 125;

    private final TreeMap<Integer, DataInfo> bySiemensRegister =
            new TreeMap<>();

    private final Map<Integer, DataInfo> byRawStartAddress =
            new LinkedHashMap<>();

    private Mag8000RegisterMap() {
    }

    public static Mag8000RegisterMap loadDefault() {
        List<Path> candidates = List.of(
                Paths.get(Mag8000Data.REGISTER_MAP_FILE),
                Paths.get("src", Mag8000Data.REGISTER_MAP_FILE)
        );

        for (Path path : candidates) {
            if (Files.exists(path)) {
                return load(path);
            }
        }

        throw new RuntimeException(
                "Không tìm thấy " + Mag8000Data.REGISTER_MAP_FILE
                        + ". Hãy đặt CSV ở project root (khuyến nghị) "
                        + "hoặc trong thư mục src."
        );
    }

    public static Mag8000RegisterMap load(Path csvPath) {
        Mag8000RegisterMap registerMap = new Mag8000RegisterMap();

        try {
            List<String> lines = Files.readAllLines(
                    csvPath,
                    StandardCharsets.UTF_8
            );

            if (lines.isEmpty()) {
                throw new RuntimeException("Register map CSV đang rỗng.");
            }

            Map<String, Integer> header = null;
            int loaded = 0;

            for (int lineNumber = 0;
                 lineNumber < lines.size();
                 lineNumber++) {

                String line = lines.get(lineNumber);

                if (lineNumber == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                List<String> columns = parseCsvLine(line);

                if (header == null) {
                    header = buildHeader(columns);
                    validateHeader(header);
                    continue;
                }

                try {
                    DataInfo info = parseDataInfo(columns, header);
                    registerMap.add(info);
                    loaded++;
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Lỗi CSV dòng " + (lineNumber + 1)
                                    + ": " + e.getMessage(),
                            e
                    );
                }
            }

            if (header == null) {
                throw new RuntimeException("CSV không có header.");
            }

            System.out.println(
                    "[OK] Loaded MAG-8000 register map: "
                            + loaded + " parameters from "
                            + csvPath.toAbsolutePath()
            );

            return registerMap;

        } catch (IOException e) {
            throw new RuntimeException(
                    "Không đọc được register map CSV: " + csvPath,
                    e
            );
        }
    }

    private void add(DataInfo info) {
        if (bySiemensRegister.containsKey(info.getSiemensRegister())) {
            throw new IllegalArgumentException(
                    "Trùng Siemens register 4:"
                            + String.format("%04d", info.getSiemensRegister())
            );
        }

        bySiemensRegister.put(
                info.getSiemensRegister(),
                info
        );

        byRawStartAddress.put(
                info.getAddress(),
                info
        );
    }

    public DataInfo getBySiemensRegister(int register) {
        return bySiemensRegister.get(register);
    }

    public DataInfo getByRawStartAddress(int rawAddress) {
        return byRawStartAddress.get(rawAddress);
    }

    // Trả về parameter đang chiếm rawAddress, kể cả rawAddress là
    // register thứ 2/3/4 của FLOAT32 / DATE / TOTALTYPE / STRING.
    public DataInfo findContainingRawAddress(int rawAddress) {
        Map.Entry<Integer, DataInfo> floor =
                bySiemensRegister.floorEntry(rawAddress + 1);

        if (floor == null) {
            return null;
        }

        DataInfo info = floor.getValue();

        if (rawAddress >= info.getAddress()
                && rawAddress < info.getEndAddressExclusive()) {
            return info;
        }

        return null;
    }

    public Collection<DataInfo> getAll() {
        return List.copyOf(bySiemensRegister.values());
    }

    public List<DataInfo> getGroup(String group) {
        List<DataInfo> result = new ArrayList<>();

        for (DataInfo info : bySiemensRegister.values()) {
            if (info.getGroup().equalsIgnoreCase(group)) {
                result.add(info);
            }
        }

        return result;
    }

    public List<DataBlock> buildDefaultMonitoringBlocks() {
        List<DataInfo> selected = new ArrayList<>();

        for (DataInfo info : bySiemensRegister.values()) {
            if (info.isDefaultMonitor() && info.isReadable()) {
                selected.add(info);
            }
        }

        return buildBlocks(selected);
    }

    public List<DataBlock> buildBlocksForGroup(String group) {
        List<DataInfo> selected = new ArrayList<>();

        for (DataInfo info : getGroup(group)) {
            if (info.isReadable()) {
                selected.add(info);
            }
        }

        return buildBlocks(selected);
    }

    private List<DataBlock> buildBlocks(List<DataInfo> selected) {
        List<DataBlock> blocks = new ArrayList<>();

        selected.sort(
                Comparator.comparing(DataInfo::getGroup)
                        .thenComparingInt(DataInfo::getAddress)
        );

        String currentGroup = null;
        List<DataInfo> current = new ArrayList<>();
        int currentStart = -1;
        int currentEndExclusive = -1;

        for (DataInfo info : selected) {
            boolean sameGroup =
                    currentGroup != null
                            && currentGroup.equals(info.getGroup());

            boolean contiguous =
                    !current.isEmpty()
                            && info.getAddress() <= currentEndExclusive;

            int proposedEnd =
                    current.isEmpty()
                            ? info.getEndAddressExclusive()
                            : Math.max(
                            currentEndExclusive,
                            info.getEndAddressExclusive()
                    );

            boolean withinLimit =
                    current.isEmpty()
                            || proposedEnd - currentStart
                            <= MAX_READ_REGISTERS;

            if (sameGroup && contiguous && withinLimit) {
                current.add(info);
                currentEndExclusive = proposedEnd;
                continue;
            }

            if (!current.isEmpty()) {
                blocks.add(createNamedBlock(currentGroup, current, blocks));
            }

            currentGroup = info.getGroup();
            current = new ArrayList<>();
            current.add(info);
            currentStart = info.getAddress();
            currentEndExclusive = info.getEndAddressExclusive();
        }

        if (!current.isEmpty()) {
            blocks.add(createNamedBlock(currentGroup, current, blocks));
        }

        return blocks;
    }

    private DataBlock createNamedBlock(
            String group,
            List<DataInfo> points,
            List<DataBlock> existingBlocks
    ) {
        long sameGroupCount = existingBlocks.stream()
                .filter(block -> block.getName().startsWith(group))
                .count();

        String name = sameGroupCount == 0
                ? group
                : group + " #" + (sameGroupCount + 1);

        return new DataBlock(name, points);
    }

    private static DataInfo parseDataInfo(
            List<String> row,
            Map<String, Integer> header
    ) {
        int siemensRegister = Integer.parseInt(
                value(row, header, "siemens_register")
        );

        String name = value(row, header, "name");

        DataType type = DataType.valueOf(
                value(row, header, "data_type")
                        .trim()
                        .toUpperCase(Locale.ROOT)
        );

        int sizeBytes = Integer.parseInt(
                value(row, header, "size_bytes")
        );

        String unit = value(row, header, "unit");

        AccessMode accessMode = AccessMode.valueOf(
                value(row, header, "access_mode")
                        .trim()
                        .toUpperCase(Locale.ROOT)
        );

        String group = value(row, header, "group");

        boolean defaultMonitor = Boolean.parseBoolean(
                value(row, header, "default_monitor")
        );

        String description = value(row, header, "description");

        return new DataInfo(
                name,
                siemensRegister,
                type,
                sizeBytes,
                unit,
                accessMode,
                group,
                defaultMonitor,
                description
        );
    }

    private static Map<String, Integer> buildHeader(
            List<String> columns
    ) {
        Map<String, Integer> header = new LinkedHashMap<>();

        for (int i = 0; i < columns.size(); i++) {
            header.put(
                    columns.get(i)
                            .trim()
                            .toLowerCase(Locale.ROOT),
                    i
            );
        }

        return header;
    }

    private static void validateHeader(
            Map<String, Integer> header
    ) {
        String[] required = {
                "siemens_register",
                "name",
                "data_type",
                "size_bytes",
                "unit",
                "access_mode",
                "group",
                "default_monitor",
                "description"
        };

        for (String column : required) {
            if (!header.containsKey(column)) {
                throw new RuntimeException(
                        "CSV thiếu cột: " + column
                );
            }
        }
    }

    private static String value(
            List<String> row,
            Map<String, Integer> header,
            String column
    ) {
        Integer index = header.get(column);

        if (index == null || index >= row.size()) {
            return "";
        }

        return row.get(index).trim();
    }

    // Parser CSV nhỏ gọn nhưng vẫn hỗ trợ field có dấu phẩy và dấu quote.
    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes
                        && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        result.add(current.toString());
        return result;
    }
}
