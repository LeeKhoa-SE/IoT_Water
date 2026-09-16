import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Mag8000Connect {

    // ============================================================
    // Windows / IrDA
    // ============================================================

    private static final int AF_IRDA = 26;
    private static final int SOCK_STREAM = 1;

    private static final int SOL_IRLMP = 0xFF;
    private static final int IRLMP_ENUMDEVICES = 0x10;

    private static final int SOL_SOCKET = 0xFFFF;
    private static final int SO_RCVTIMEO = 0x1006;

    private static final long INVALID_SOCKET = -1L;

    private static final int SOCKADDR_IRDA_SIZE = 31;
    private static final int IRDA_DEVICE_INFO_SIZE = 4 + 22 + 1 + 1 + 1;
    private static final int DEVICE_LIST_LEN = 10;

    private static final int SLAVE_ID = 1;
    private static final int MAX_READ_REGISTERS = 125;


    // ============================================================
    // Winsock
    // ============================================================

    private interface Ws2_32 extends Library {

        Ws2_32 INSTANCE =
                Native.load("Ws2_32", Ws2_32.class);

        int WSAStartup(short version, Pointer data);

        int WSACleanup();

        long socket(int af, int type, int protocol);

        int getsockopt(
                long socket,
                int level,
                int optionName,
                Pointer optionValue,
                IntByReference optionLength
        );

        int setsockopt(
                long socket,
                int level,
                int optionName,
                Pointer optionValue,
                int optionLength
        );

        int connect(long socket, Pointer name, int nameLength);

        int send(long socket, byte[] buffer, int length, int flags);

        int recv(long socket, byte[] buffer, int length, int flags);

        int closesocket(long socket);

        int WSAGetLastError();
    }


    private final Ws2_32 ws = Ws2_32.INSTANCE;
    private long socket = INVALID_SOCKET;


    // ============================================================
    // CONNECT
    // ============================================================

    public void connect() {

        System.out.println("[INFO] Starting MAG-8000 connection...");

        Memory wsaData = new Memory(400);

        int startupResult =
                ws.WSAStartup(
                        (short) 0x0202,
                        wsaData
                );

        if (startupResult != 0) {
            throw new RuntimeException(
                    "WSAStartup failed. Error = " + startupResult
            );
        }

        socket =
                ws.socket(
                        AF_IRDA,
                        SOCK_STREAM,
                        0
                );

        if (socket == INVALID_SOCKET) {
            throw new RuntimeException(
                    "Cannot create IrDA socket. Error = "
                            + ws.WSAGetLastError()
            );
        }

        System.out.println("[OK] IrDA socket created.");

        Memory deviceList =
                new Memory(
                        4L + (long) IRDA_DEVICE_INFO_SIZE * DEVICE_LIST_LEN
                );

        deviceList.clear();

        IntByReference deviceListLength =
                new IntByReference((int) deviceList.size());

        int result =
                ws.getsockopt(
                        socket,
                        SOL_IRLMP,
                        IRLMP_ENUMDEVICES,
                        deviceList,
                        deviceListLength
                );

        if (result != 0) {
            throw new RuntimeException(
                    "Cannot discover IrDA device. Error = "
                            + ws.WSAGetLastError()
            );
        }

        int numberOfDevices = deviceList.getInt(0);

        if (numberOfDevices <= 0) {
            throw new RuntimeException("Không tìm thấy MAG-8000.");
        }

        byte[] deviceId = deviceList.getByteArray(4, 4);
        byte[] nameBytes = deviceList.getByteArray(8, 22);

        String deviceName = readNullTerminatedAscii(nameBytes);

        System.out.println("[OK] Device found: " + deviceName);
        System.out.println("[INFO] Device ID: " + bytesToHex(deviceId));

        Memory address =
                createIrdaAddress(
                        deviceId,
                        "IrDA:IrCOMM"
                );

        int connectResult =
                ws.connect(
                        socket,
                        address,
                        SOCKADDR_IRDA_SIZE
                );

        if (connectResult != 0) {
            throw new RuntimeException(
                    "Cannot connect to MAG-8000. Error = "
                            + ws.WSAGetLastError()
            );
        }

        System.out.println("[OK] Connected to MAG-8000.");

        Memory timeout = new Memory(4);
        timeout.setInt(0, 1000);

        int timeoutResult =
                ws.setsockopt(
                        socket,
                        SOL_SOCKET,
                        SO_RCVTIMEO,
                        timeout,
                        4
                );

        if (timeoutResult != 0) {
            System.out.println(
                    "[WARNING] Cannot set receive timeout. Error = "
                            + ws.WSAGetLastError()
            );
        }
    }


    // ============================================================
    // CLOSE
    // ============================================================

    public void close() {

        if (socket != INVALID_SOCKET) {
            ws.closesocket(socket);
            socket = INVALID_SOCKET;
        }

        ws.WSACleanup();
        System.out.println("[INFO] Connection closed.");
    }


    // ============================================================
    // READ ONE DATA POINT
    // Dùng khi chỉ cần đúng một giá trị.
    // ============================================================

    public Object read(DataInfo info) {

        if (!info.isReadable()) {
            throw new IllegalArgumentException(
                    info.getName() + " là WRITE_ONLY nên không thể đọc."
            );
        }

        byte[] data =
                readRegisters(
                        info.getAddress(),
                        info.getRegisterCount()
                );

        return decodeValue(
                info,
                data,
                0
        );
    }


    // Scanner dùng bản silent để không in SEND/RECV cho từng parameter.
    public Object readSilent(DataInfo info) {

        if (!info.isReadable()) {
            throw new IllegalArgumentException(
                    info.getName() + " là WRITE_ONLY nên không thể đọc."
            );
        }

        byte[] data =
                readRegistersSilent(
                        info.getAddress(),
                        info.getRegisterCount()
                );

        return decodeValue(
                info,
                data,
                0
        );
    }


    public void printRegister(DataInfo info) {

        try {
            Object value = read(info);
            printValue(info, value);
        } catch (Exception e) {
            System.out.println(
                    info.getName()
                            + " : ERROR -> "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // READ BLOCK
    // Một block = một Modbus request.
    // ============================================================

    public Map<DataInfo, Object> readBlock(DataBlock block) {

        byte[] blockData =
                readRegisters(
                        block.getStartAddress(),
                        block.getRegisterCount()
                );

        Map<DataInfo, Object> result =
                new LinkedHashMap<>();

        for (DataInfo info : block.getDataPoints()) {

            int registerOffset =
                    info.getAddress()
                            - block.getStartAddress();

            int byteOffset =
                    registerOffset * 2;

            Object value =
                    decodeValue(
                            info,
                            blockData,
                            byteOffset
                    );

            result.put(info, value);
        }

        return result;
    }


    public void printBlock(DataBlock block) {

        System.out.println();
        System.out.println("--------------------------------------");
        System.out.println(" " + block.getName());
        System.out.println("--------------------------------------");

        try {

            Map<DataInfo, Object> values =
                    readBlock(block);

            for (Map.Entry<DataInfo, Object> entry
                    : values.entrySet()) {

                printValue(
                        entry.getKey(),
                        entry.getValue()
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "[BLOCK ERROR] "
                            + block.getName()
                            + " -> "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // DECODE VALUE FROM A BLOCK BUFFER
    // ============================================================

    public Object decodeValue(
            DataInfo info,
            byte[] data,
            int byteOffset
    ) {

        int transportBytes =
                info.getRegisterCount() * 2;

        if (byteOffset < 0
                || byteOffset + transportBytes > data.length) {

            throw new RuntimeException(
                    "Không đủ byte để decode " + info.getName()
            );
        }

        switch (info.getType()) {

            case UINT8:
                // Siemens: uint8 nằm ở byte thấp của một register 16-bit.
                return data[byteOffset + 1] & 0xFF;

            case UINT16:
                return ((data[byteOffset] & 0xFF) << 8)
                        | (data[byteOffset + 1] & 0xFF);

            case UINT32:
                return ((long) (data[byteOffset] & 0xFF) << 24)
                        | ((long) (data[byteOffset + 1] & 0xFF) << 16)
                        | ((long) (data[byteOffset + 2] & 0xFF) << 8)
                        | ((long) (data[byteOffset + 3] & 0xFF));

            case INT32:
                return ByteBuffer
                        .wrap(data, byteOffset, 4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .getInt();

            case FLOAT32:
                return ByteBuffer
                        .wrap(data, byteOffset, 4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .getFloat();

            case FLOAT64:
                return ByteBuffer
                        .wrap(data, byteOffset, 8)
                        .order(ByteOrder.BIG_ENDIAN)
                        .getDouble();

            case STRING:
                int stringLength = Math.min(
                        info.getSizeBytes(),
                        transportBytes
                );

                int actualLength = 0;

                while (actualLength < stringLength
                        && data[byteOffset + actualLength] != 0) {
                    actualLength++;
                }

                return new String(
                        data,
                        byteOffset,
                        actualLength,
                        StandardCharsets.US_ASCII
                ).trim();

            case DATE:
                if (info.getSizeBytes() < 6) {
                    throw new RuntimeException(
                            "DATE cần ít nhất 6 byte: " + info.getName()
                    );
                }

                int year = data[byteOffset] & 0xFF;
                int month = data[byteOffset + 1] & 0xFF;
                int day = data[byteOffset + 2] & 0xFF;
                int hour = data[byteOffset + 3] & 0xFF;
                int minute = data[byteOffset + 4] & 0xFF;
                int second = data[byteOffset + 5] & 0xFF;

                return String.format(
                        "%02d-%02d-%02d %02d:%02d:%02d",
                        year,
                        month,
                        day,
                        hour,
                        minute,
                        second
                );

            case TOTALTYPE:
                ByteBuffer totalBuffer =
                        ByteBuffer
                                .wrap(data, byteOffset, 8)
                                .order(ByteOrder.BIG_ENDIAN);

                int integerPart = totalBuffer.getInt();
                int decimalPart = totalBuffer.getInt();

                return integerPart
                        + decimalPart / 1_000_000_000.0;

            default:
                throw new RuntimeException(
                        "Unsupported data type: "
                                + info.getType()
                );
        }
    }


    private void printValue(
            DataInfo info,
            Object value
    ) {

        System.out.print(
                info.getName()
                        + " : "
                        + value
        );

        if (info.getUnit() != null
                && !info.getUnit().isEmpty()) {

            System.out.print(" " + info.getUnit());
        }

        System.out.println();
    }


    // ============================================================
    // RAW REGISTER READ
    // Public: dùng cho block và debug.
    // ============================================================

    public byte[] readRegisters(
            int register,
            int quantity
    ) {
        return readRegistersInternal(
                register,
                quantity,
                true
        );
    }


    // Scanner dùng bản silent để không in hàng nghìn dòng SEND/RECV.
    public byte[] readRegistersSilent(
            int register,
            int quantity
    ) {
        return readRegistersInternal(
                register,
                quantity,
                false
        );
    }


    private byte[] readRegistersInternal(
            int register,
            int quantity,
            boolean verbose
    ) {

        if (socket == INVALID_SOCKET) {
            throw new RuntimeException("MAG-8000 chưa được connect.");
        }

        if (register < 0 || register > 0xFFFF) {
            throw new IllegalArgumentException(
                    "Register address không hợp lệ: " + register
            );
        }

        if (quantity <= 0 || quantity > MAX_READ_REGISTERS) {
            throw new IllegalArgumentException(
                    "Quantity phải nằm trong 1.."
                            + MAX_READ_REGISTERS
            );
        }

        if ((long) register + quantity - 1 > 0xFFFFL) {
            throw new IllegalArgumentException(
                    "Khoảng register vượt quá 0xFFFF."
            );
        }

        byte[] request =
                buildReadHoldingRegisterRequest(
                        SLAVE_ID,
                        register,
                        quantity
                );

        if (verbose) {
            System.out.println();
            System.out.println(
                    "[SEND] Block start="
                            + register
                            + ", quantity="
                            + quantity
                            + " -> "
                            + bytesToHex(request)
            );
        }

        int sent =
                ws.send(
                        socket,
                        request,
                        request.length,
                        0
                );

        if (sent == -1) {
            throw new RuntimeException(
                    "Modbus send failed. Error = "
                            + ws.WSAGetLastError()
            );
        }

        byte[] response = new byte[256];
        int totalReceived = 0;
        int expectedLength = -1;

        while (true) {

            byte[] temp = new byte[256];

            int received =
                    ws.recv(
                            socket,
                            temp,
                            temp.length,
                            0
                    );

            if (received > 0) {

                if (totalReceived + received > response.length) {
                    throw new RuntimeException(
                            "Response buffer overflow."
                    );
                }

                System.arraycopy(
                        temp,
                        0,
                        response,
                        totalReceived,
                        received
                );

                totalReceived += received;

                if (totalReceived >= 2) {

                    int function = response[1] & 0xFF;

                    if ((function & 0x80) != 0) {

                        if (totalReceived >= 3) {
                            int exceptionCode =
                                    response[2] & 0xFF;

                            throw new RuntimeException(
                                    "Modbus exception code = "
                                            + exceptionCode
                            );
                        }
                    }
                }

                if (totalReceived >= 3) {

                    int byteCount = response[2] & 0xFF;
                    expectedLength = 3 + byteCount;

                    if (totalReceived >= expectedLength) {
                        break;
                    }
                }

            } else {

                int error = ws.WSAGetLastError();

                if (error == 0 || error == 10060) {
                    break;
                }

                throw new RuntimeException(
                        "Socket recv error = " + error
                );
            }
        }

        if (totalReceived < 3) {
            throw new RuntimeException("Response quá ngắn.");
        }

        int responseSlave = response[0] & 0xFF;
        int function = response[1] & 0xFF;

        if (responseSlave != SLAVE_ID) {
            throw new RuntimeException(
                    "Sai Slave ID. Received = " + responseSlave
            );
        }

        if (function != 0x03) {
            throw new RuntimeException(
                    "Sai Function Code. Received = " + function
            );
        }

        int byteCount = response[2] & 0xFF;
        int expectedDataBytes = quantity * 2;

        if (byteCount != expectedDataBytes) {
            throw new RuntimeException(
                    "Byte count không đúng. Expected = "
                            + expectedDataBytes
                            + ", received = "
                            + byteCount
            );
        }

        if (totalReceived < 3 + byteCount) {
            throw new RuntimeException("Incomplete Modbus data.");
        }

        byte[] data =
                Arrays.copyOfRange(
                        response,
                        3,
                        3 + byteCount
                );

        if (verbose) {
            System.out.println(
                    "[RECV] "
                            + bytesToHex(
                            Arrays.copyOf(
                                    response,
                                    totalReceived
                            )
                    )
            );

            System.out.println(
                    "[DATA] " + bytesToHex(data)
            );
        }

        return data;
    }


    // ============================================================
    // IRDA ADDRESS
    // ============================================================

    private Memory createIrdaAddress(
            byte[] deviceId,
            String serviceName
    ) {

        Memory address = new Memory(SOCKADDR_IRDA_SIZE);
        address.clear();

        address.setShort(0, (short) AF_IRDA);
        address.write(2, deviceId, 0, 4);

        byte[] service =
                serviceName.getBytes(StandardCharsets.US_ASCII);

        if (service.length >= 25) {
            throw new IllegalArgumentException(
                    "IrDA service name too long."
            );
        }

        address.write(6, service, 0, service.length);
        address.setByte(6 + service.length, (byte) 0);

        return address;
    }


    // ============================================================
    // MODBUS FUNCTION 03 REQUEST
    // ============================================================

    private byte[] buildReadHoldingRegisterRequest(
            int slave,
            int register,
            int quantity
    ) {

        byte[] frame = new byte[8];

        frame[0] = (byte) slave;
        frame[1] = 0x03;

        frame[2] = (byte) ((register >> 8) & 0xFF);
        frame[3] = (byte) (register & 0xFF);

        frame[4] = (byte) ((quantity >> 8) & 0xFF);
        frame[5] = (byte) (quantity & 0xFF);

        int crc = modbusCRC(frame, 0, 6);

        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);

        return frame;
    }


    private int modbusCRC(
            byte[] data,
            int offset,
            int length
    ) {

        int crc = 0xFFFF;

        for (int i = offset; i < offset + length; i++) {

            crc ^= data[i] & 0xFF;

            for (int j = 0; j < 8; j++) {

                if ((crc & 1) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }

        return crc & 0xFFFF;
    }


    // ============================================================
    // HELPERS
    // ============================================================

    private String readNullTerminatedAscii(byte[] data) {

        int length = 0;

        while (length < data.length
                && data[length] != 0) {
            length++;
        }

        return new String(
                data,
                0,
                length,
                StandardCharsets.US_ASCII
        ).trim();
    }


    public String bytesToHex(byte[] data) {

        StringBuilder sb = new StringBuilder();

        for (byte b : data) {

            if (sb.length() > 0) {
                sb.append(" ");
            }

            sb.append(
                    String.format(
                            "%02X",
                            b & 0xFF
                    )
            );
        }

        return sb.toString();
    }
}
