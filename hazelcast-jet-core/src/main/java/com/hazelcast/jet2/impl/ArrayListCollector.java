/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet2.impl;

import com.hazelcast.jet2.Cursor;
import com.hazelcast.jet2.OutputCollector;

import java.util.ArrayList;
import java.util.List;

public class ArrayListCollector<T> implements OutputCollector<T> {

    private final ArrayList<T> buffer;
    private final ListCursor<T> cursor;

    public ArrayListCollector() {
        buffer = new ArrayList<>();
        cursor = new ListCursor<>(buffer);
    }

    @Override
    public void collect(T object) {
        buffer.add(object);
    }

    public void clear() {
        buffer.clear();
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public Cursor<T> cursor() {
        return cursor;
    }

    public List<T> buffer() {
        return buffer;
    }

}
