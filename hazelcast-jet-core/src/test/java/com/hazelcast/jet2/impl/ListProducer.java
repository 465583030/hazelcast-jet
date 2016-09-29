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

import com.hazelcast.jet2.OutputCollector;
import com.hazelcast.jet2.Producer;

import java.util.Iterator;
import java.util.List;

public class ListProducer<T> implements Producer<T> {

    private Iterator<T> iterator;
    private boolean skipNextCall;

    public ListProducer(List<T> list) {
        this.iterator = list.iterator();
    }

    public void skipNext() {
        skipNextCall = true;
    }
    @Override
    public boolean produce(OutputCollector<T> collector) {
        if (iterator.hasNext()) {
            if (skipNextCall) {
                skipNextCall = false;
                return false;
            }

            collector.collect(iterator.next());
            return !iterator.hasNext();
        }
        return true;
    }
}
