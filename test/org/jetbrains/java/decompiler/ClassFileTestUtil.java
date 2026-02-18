package org.jetbrains.java.decompiler;

final class ClassFileTestUtil {
  private ClassFileTestUtil() {
  }

  static int u1(byte[] bytes, int offset) {
    return bytes[offset] & 0xFF;
  }

  static int u2(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
  }

  static int u4(byte[] bytes, int offset) {
    return (u2(bytes, offset) << 16) | u2(bytes, offset + 2);
  }

  static void putU2(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte)((value >>> 8) & 0xFF);
    bytes[offset + 1] = (byte)(value & 0xFF);
  }

  static int skipMember(byte[] bytes, int offset) {
    offset += 6; // access_flags, name_index, descriptor_index
    int attributesCount = u2(bytes, offset);
    offset += 2;
    for (int i = 0; i < attributesCount; i++) {
      int attrLength = u4(bytes, offset + 2);
      offset += 6 + attrLength;
    }
    return offset;
  }
}
