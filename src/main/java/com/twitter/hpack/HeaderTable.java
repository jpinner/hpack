/*
 * Copyright 2013 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.hpack;

import static com.twitter.hpack.HeaderField.HEADER_ENTRY_OVERHEAD;

final class HeaderTable<T extends HeaderField> {

  // a circular queue of reference headers
  private HeaderField[] headerTable;
  private int head;
  private int tail;
  private int size;
  private int capacity;

  HeaderTable(int initialCapacity) {
    setCapacity(initialCapacity);
  }

  /**
   * Return the number of header fields in the header table.
   */
  public int length() {
    int length;
    if (head < tail) {
      length = headerTable.length - tail + head;
    } else {
      length = head - tail;
    }
    return length;
  }

  /**
   * Return the current size of the header table.
   * This is the sum of the size of the entries.
   */
  public int size() {
    return size;
  }

  /**
   * Return the maximum allowable size of the header table.
   */
  public int capacity() {
    return capacity;
  }

  /**
   * Return the header field at the given index.
   * The first and newest entry is always at index 1,
   * and the oldest entry is at the index length().
   */
  @SuppressWarnings("unchecked")
  public T getEntry(int index) {
    if (index <= 0 || index > length()) {
      throw new IndexOutOfBoundsException();
    }
    int i = head - index;
    if (i < 0) {
      return (T) headerTable[i + headerTable.length];
    } else {
      return (T) headerTable[i];
    }
  }

  /**
   * Returns the lowest index value for the header field name in the header table.
   * Returns -1 if the header field name is not in the header table.
   */
  public int getIndex(String name) {
    int index = -1;
    int cursor = tail;
    while (cursor != head) {
      HeaderField entry = headerTable[cursor];
      if (equals(name, entry.name)) {
        index = cursor;
      }
      if (++cursor == headerTable.length) {
        cursor = 0;
      }
    }
    return getOffset(index);
  }

  /**
   * Returns the lowest index value for the header field in the header table.
   * Returns -1 if the header field is not in the header table.
   */
  public int getIndex(String name, String value) {
    int index = -1;
    int cursor = tail;
    while (cursor != head) {
      HeaderField entry = headerTable[cursor];
      boolean nameMatches = equals(name, entry.name);
      boolean valueMatches = equals(value, entry.value);
      if (nameMatches && valueMatches) {
        index = cursor;
      }
      if (++cursor == headerTable.length) {
        cursor = 0;
      }
    }
    return getOffset(index);
  }

  private int getOffset(int index) {
    if (index == -1) {
      return index;
    } else if (index < head) {
      return head - index;
    } else {
      return headerTable.length - index + head;
    }
  }

  public void add(T header) {
    int headerSize = header.size();
    if (headerSize > capacity) {
      clear();
      return;
    }
    while (size + headerSize > capacity) {
      remove();
    }
    headerTable[head++] = header;
    size += header.size();
    if (head == headerTable.length) {
      head = 0;
    }
  }

  public void setCapacity(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("Illegal Capacity: "+ capacity);
    }
    int maxEntries = capacity / HEADER_ENTRY_OVERHEAD;
    if (capacity % HEADER_ENTRY_OVERHEAD != 0) {
      maxEntries++;
    }
    HeaderField[] tmp = new HeaderField[maxEntries];

    // initially size will be 0 so remove won't be called
    while (size > capacity) {
      remove();
    }

    // initially length will be 0 so there will be no copy
    int len = length();
    int cursor = tail;
    for (int i = 0; i < len; i++) {
      tmp[i] = headerTable[cursor++];
      if (cursor == headerTable.length) {
        cursor = 0;
      }
    }

    this.capacity = capacity;
    this.headerTable = tmp;
  }

  @SuppressWarnings("unchecked")
  public T remove() {
    T removed = (T) headerTable[tail];
    size -= removed.size();
    headerTable[tail++] = null;
    if (tail == headerTable.length) {
      tail = 0;
    }
    return removed;
  }

  public void clear() {
    // null out entries;
    while (tail != head) {
      headerTable[tail++] = null;
      if (tail == headerTable.length) {
        tail = 0;
      }
    }
    head = 0;
    tail = 0;
    size = 0;
  }

  /**
   * A string compare that doesn't leak timing information.
   */
  private static boolean equals(String s1, String s2) {
    if (s1.length() != s2.length()) {
      return false;
    }
    char c = 0;
    for (int i = 0; i < s1.length(); i++) {
      c |= (s1.charAt(i) ^ s2.charAt(i));
    }
    return c == 0;
  }
}
