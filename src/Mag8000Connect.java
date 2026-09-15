import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

    private static final int IRDA_DEVICE_INFO_SIZE =
            4 + 22 + 1 + 1 + 1;

    private static final int DEVICE_LIST_LEN = 10;

    private static final int SLAVE_ID = 1;


    // ============================================================
    // Winsock
    // ============================================================

    private interface Ws2_32 extends Library {

        Ws2_32 INSTANCE =
                Native.load(
                        "Ws2_32",
                        Ws2_32.class
                );

        int WSAStartup(
                short version,
                Pointer data
        );

        int WSACleanup();

        long socket(
                int af,
                int type,
                int protocol
        );

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

        int connect(
                long socket,
                Pointer name,
                int nameLength
        );

        int send(
                long socket,
                byte[] buffer,
                int length,
                int flags
        );

        int recv(
                long socket,
                byte[] buffer,
                int length,
                int flags
        );

        int closesocket(long socket);

        int WSAGetLastError();
    }


    private final Ws2_32 ws =
            Ws2_32.INSTANCE;

    private long socket =
            INVALID_SOCKET;


    // ============================================================
    // CONNECT
    // ============================================================

    public void connect() {

        System.out.println(
                "[INFO] Starting MAG-8000 connection..."
        );

        Memory wsaData =
                new Memory(400);

        int startupResult =
                ws.WSAStartup(
                        (short) 0x0202,
                        wsaData
                );

        if (startupResult != 0) {

            throw new RuntimeException(
                    "WSAStartup failed. Error = "
                            + startupResult
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

        System.out.println(
                "[OK] IrDA socket created."
        );


        // ========================================================
        // Discover device
        // ========================================================

        Memory deviceList =
                new Memory(
                        4L
                                + (long) IRDA_DEVICE_INFO_SIZE
                                * DEVICE_LIST_LEN
                );

        deviceList.clear();

        IntByReference deviceListLength =
                new IntByReference(
                        (int) deviceList.size()
                );

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
                    "Cannot discover IrDA device."
            );
        }

        int numberOfDevices =
                deviceList.getInt(0);

        if (numberOfDevices <= 0) {

            throw new RuntimeException(
                    "Không tìm thấy MAG-8000."
            );
        }

        byte[] deviceId =
                deviceList.getByteArray(
                        4,
                        4
                );

        byte[] nameBytes =
                deviceList.getByteArray(
                        8,
                        22
                );

        String deviceName =
                readNullTerminatedAscii(
                        nameBytes
                );

        System.out.println(
                "[OK] Device found: "
                        + deviceName
        );

        System.out.println(
                "[INFO] Device ID: "
                        + bytesToHex(deviceId)
        );


        // ========================================================
        // IrDA address
        // ========================================================

        Memory address =
                createIrdaAddress(
                        deviceId,
                        "IrDA:IrCOMM"
                );


        // ========================================================
        // Connect
        // ========================================================

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

        System.out.println(
                "[OK] Connected to MAG-8000."
        );


        // ========================================================
        // Receive timeout
        // ========================================================

        Memory timeout =
                new Memory(4);

        timeout.setInt(
                0,
                1000
        );

        ws.setsockopt(
                socket,
                SOL_SOCKET,
                SO_RCVTIMEO,
                timeout,
                4
        );
    }


    // ============================================================
    // CLOSE
    // ============================================================

    public void close() {

        if (socket != INVALID_SOCKET) {

            ws.closesocket(
                    socket
            );

            socket =
                    INVALID_SOCKET;
        }

        ws.WSACleanup();

        System.out.println(
                "[INFO] Connection closed."
        );
    }


    // ============================================================
    // READ UINT16
    // ============================================================

    public int readUInt16(
            int register
    ) {

        byte[] data =
                readRegisters(
                        register,
                        1
                );

        return ((data[0] & 0xFF) << 8)
                |
                (data[1] & 0xFF);
    }


    // ============================================================
    // READ FLOAT32
    // ============================================================

    public float readFloat32(
            int register
    ) {

        byte[] data =
                readRegisters(
                        register,
                        2
                );

        ByteBuffer buffer =
                ByteBuffer.wrap(data);

        buffer.order(
                ByteOrder.BIG_ENDIAN
        );

        return buffer.getFloat();
    }


    // ============================================================
    // READ 64 BIT VALUE
    // ============================================================

    public long readInt64(
            int register
    ) {

        byte[] data =
                readRegisters(
                        register,
                        4
                );

        ByteBuffer buffer =
                ByteBuffer.wrap(data);

        buffer.order(
                ByteOrder.BIG_ENDIAN
        );

        return buffer.getLong();
    }


    // ============================================================
    // READ RAW REGISTERS
    // ============================================================

    public byte[] readRegisters(
            int register,
            int quantity
    ) {

        if (socket == INVALID_SOCKET) {

            throw new RuntimeException(
                    "MAG-8000 chưa được connect."
            );
        }

        byte[] request =
                buildReadHoldingRegisterRequest(
                        SLAVE_ID,
                        register,
                        quantity
                );

        System.out.println();

        System.out.println(
                "[SEND] Register "
                        + register
                        + " -> "
                        + bytesToHex(request)
        );


        // ========================================================
        // SEND
        // ========================================================

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


        // ========================================================
        // RECEIVE
        // ========================================================

        byte[] response =
                new byte[256];

        int totalReceived = 0;

        int expectedLength = -1;

        while (true) {

            byte[] temp =
                    new byte[256];

            int received =
                    ws.recv(
                            socket,
                            temp,
                            temp.length,
                            0
                    );

            if (received > 0) {

                System.arraycopy(
                        temp,
                        0,
                        response,
                        totalReceived,
                        received
                );

                totalReceived +=
                        received;


                // =================================================
                // Need at least:
                //
                // Slave
                // Function
                // Byte count
                // =================================================

                if (totalReceived >= 3) {

                    int function =
                            response[1] & 0xFF;


                    // =============================================
                    // Exception
                    // =============================================

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


                    // =============================================
                    // Function 03
                    // =============================================

                    int byteCount =
                            response[2] & 0xFF;

                    expectedLength =
                            3 + byteCount;

                    if (totalReceived
                            >= expectedLength) {

                        break;
                    }
                }

            } else {

                int error =
                        ws.WSAGetLastError();

                if (error == 0
                        || error == 10060) {

                    break;
                }

                throw new RuntimeException(
                        "Socket recv error = "
                                + error
                );
            }
        }


        // ========================================================
        // VALIDATE
        // ========================================================

        if (totalReceived < 3) {

            throw new RuntimeException(
                    "Response quá ngắn."
            );
        }

        int responseSlave =
                response[0] & 0xFF;

        int function =
                response[1] & 0xFF;

        if (responseSlave != SLAVE_ID) {

            throw new RuntimeException(
                    "Sai Slave ID."
            );
        }

        if (function != 0x03) {

            throw new RuntimeException(
                    "Sai Function Code."
            );
        }

        int byteCount =
                response[2] & 0xFF;

        if (totalReceived
                < 3 + byteCount) {

            throw new RuntimeException(
                    "Incomplete Modbus data."
            );
        }


        // ========================================================
        // COPY DATA ONLY
        // ========================================================

        byte[] data =
                Arrays.copyOfRange(
                        response,
                        3,
                        3 + byteCount
                );

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
                "[DATA] "
                        + bytesToHex(data)
        );

        return data;
    }


    // ============================================================
    // CREATE IRDA ADDRESS
    // ============================================================

    private Memory createIrdaAddress(
            byte[] deviceId,
            String serviceName
    ) {

        Memory address =
                new Memory(
                        SOCKADDR_IRDA_SIZE
                );

        address.clear();

        address.setShort(
                0,
                (short) AF_IRDA
        );

        address.write(
                2,
                deviceId,
                0,
                4
        );

        byte[] service =
                serviceName.getBytes(
                        StandardCharsets.US_ASCII
                );

        address.write(
                6,
                service,
                0,
                service.length
        );

        address.setByte(
                6 + service.length,
                (byte) 0
        );

        return address;
    }


    // ============================================================
    // BUILD MODBUS REQUEST
    // ============================================================

    private byte[] buildReadHoldingRegisterRequest(
            int slave,
            int register,
            int quantity
    ) {

        byte[] frame =
                new byte[8];

        frame[0] =
                (byte) slave;

        frame[1] =
                0x03;

        frame[2] =
                (byte) (
                        (register >> 8)
                                & 0xFF
                );

        frame[3] =
                (byte) (
                        register
                                & 0xFF
                );

        frame[4] =
                (byte) (
                        (quantity >> 8)
                                & 0xFF
                );

        frame[5] =
                (byte) (
                        quantity
                                & 0xFF
                );

        int crc =
                modbusCRC(
                        frame,
                        0,
                        6
                );

        frame[6] =
                (byte) (
                        crc & 0xFF
                );

        frame[7] =
                (byte) (
                        (crc >> 8)
                                & 0xFF
                );

        return frame;
    }


    // ============================================================
    // MODBUS CRC
    // ============================================================

    private int modbusCRC(
            byte[] data,
            int offset,
            int length
    ) {

        int crc =
                0xFFFF;

        for (int i = offset;
             i < offset + length;
             i++) {

            crc ^=
                    data[i] & 0xFF;

            for (int j = 0;
                 j < 8;
                 j++) {

                if ((crc & 1) != 0) {

                    crc =
                            (crc >> 1)
                                    ^ 0xA001;

                } else {

                    crc >>= 1;
                }
            }
        }

        return crc & 0xFFFF;
    }


    // ============================================================
    // ASCII
    // ============================================================

    private String readNullTerminatedAscii(
            byte[] data
    ) {

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


    // ============================================================
    // HEX
    // ============================================================

    private String bytesToHex(
            byte[] data
    ) {

        StringBuilder sb =
                new StringBuilder();

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

    public short readInt16(int register) {

        byte[] data =
                readRegisters(
                        register,
                        1
                );

        return (short) (
                ((data[0] & 0xFF) << 8)
                        |
                        (data[1] & 0xFF)
        );
    }

    public long readUInt32(int register) {

        byte[] data =
                readRegisters(
                        register,
                        2
                );

        return ((long) (data[0] & 0xFF) << 24)
                |
                ((long) (data[1] & 0xFF) << 16)
                |
                ((long) (data[2] & 0xFF) << 8)
                |
                ((long) (data[3] & 0xFF));
    }

    public int readInt32(int register) {

        byte[] data =
                readRegisters(
                        register,
                        2
                );

        ByteBuffer buffer =
                ByteBuffer.wrap(data);

        buffer.order(
                ByteOrder.BIG_ENDIAN
        );

        return buffer.getInt();
    }

    public double readFloat64(int register) {

        byte[] data =
                readRegisters(
                        register,
                        4
                );

        ByteBuffer buffer =
                ByteBuffer.wrap(data);

        buffer.order(
                ByteOrder.BIG_ENDIAN
        );

        return buffer.getDouble();
    }

    public Object read(
            DataInfo register
    ) {

        switch (register.getType()) {

            case UINT16:
                return readUInt16(
                        register.getAddress()
                );

            case INT16:
                return readInt16(
                        register.getAddress()
                );

            case UINT32:
                return readUInt32(
                        register.getAddress()
                );

            case INT32:
                return readInt32(
                        register.getAddress()
                );

            case FLOAT32:
                return readFloat32(
                        register.getAddress()
                );

            case INT64:
                return readInt64(
                        register.getAddress()
                );

            case FLOAT64:
                return readFloat64(
                        register.getAddress()
                );

            case TOTALTYPE:
                return readTotalType(
                        register.getAddress()
                );

            default:
                throw new RuntimeException(
                        "Unsupported data type: "
                                + register.getType()
                );
        }
    }

    public void printRegister(
            DataInfo register
    ) {

        try {

            Object value =
                    read(register);

            String unit =
                    register.getUnit();

            System.out.print(
                    register.getName()
                            + " : "
                            + value
            );

            if (unit != null
                    && !unit.isEmpty()) {

                System.out.print(
                        " " + unit
                );
            }

            System.out.println();

        } catch (Exception e) {

            System.out.println(
                    register.getName()
                            + " : ERROR -> "
                            + e.getMessage()
            );
        }
    }



    public double readTotalType(int register) {

        // TotalType = 8 bytes = 4 Modbus registers
        byte[] data =
                readRegisters(
                        register,
                        4
                );

        if (data.length != 8) {
            throw new RuntimeException(
                    "TOTALTYPE cần 8 byte, nhưng nhận được "
                            + data.length
                            + " byte."
            );
        }

        ByteBuffer buffer =
                ByteBuffer.wrap(data);

        buffer.order(
                ByteOrder.BIG_ENDIAN
        );

        // 4 byte đầu
        int integerPart =
                buffer.getInt();

        // 4 byte sau
        int decimalPart =
                buffer.getInt();

        return integerPart
                + decimalPart / 1_000_000_000.0;
    }
}
