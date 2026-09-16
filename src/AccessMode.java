public enum AccessMode {
    READ_ONLY,
    READ_WRITE,
    WRITE_ONLY;

    public boolean isReadable() {
        return this != WRITE_ONLY;
    }

    public boolean isWritable() {
        return this == READ_WRITE || this == WRITE_ONLY;
    }
}
