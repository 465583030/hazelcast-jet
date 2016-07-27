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

package com.hazelcast.jet.impl.container.task.processors;


import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.container.ProcessorContext;
import com.hazelcast.jet.data.io.ProducerInputStream;
import com.hazelcast.jet.impl.actor.ObjectProducer;
import com.hazelcast.jet.impl.container.ContainerContext;
import com.hazelcast.jet.impl.container.task.TaskProcessor;
import com.hazelcast.jet.impl.data.io.DefaultObjectIOStream;
import com.hazelcast.jet.impl.util.JetUtil;
import com.hazelcast.jet.processor.ContainerProcessor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static com.hazelcast.util.Preconditions.checkNotNull;

@SuppressFBWarnings("EI_EXPOSE_REP")
public class ProducerTaskProcessor implements TaskProcessor {
    protected final int taskID;
    protected final ObjectProducer[] producers;
    protected final ContainerProcessor processor;
    protected final ContainerContext containerContext;
    protected final ProcessorContext processorContext;
    protected final DefaultObjectIOStream objectInputStream;
    protected final DefaultObjectIOStream pairOutputStream;
    protected boolean produced;
    protected boolean finalized;
    protected boolean finalizationStarted;
    protected boolean finalizationFinished;
    protected ObjectProducer pendingProducer;
    private int nextProducerIdx;

    private boolean producingReadFinished;

    private boolean producersWriteFinished;

    public ProducerTaskProcessor(ObjectProducer[] producers,
                                 ContainerProcessor processor,
                                 ContainerContext containerContext,
                                 ProcessorContext processorContext,
                                 int taskID) {
        checkNotNull(processor);

        this.taskID = taskID;
        this.producers = producers;
        this.processor = processor;
        this.processorContext = processorContext;
        this.containerContext = containerContext;
        JobConfig jobConfig = containerContext.getJobContext().getJobConfig();
        int pairChunkSize = jobConfig.getChunkSize();
        this.objectInputStream = new DefaultObjectIOStream<>(new Object[pairChunkSize]);
        this.pairOutputStream = new DefaultObjectIOStream<>(new Object[pairChunkSize]);
    }

    public boolean onChunk(ProducerInputStream inputStream) throws Exception {
        return true;
    }

    protected void checkFinalization() {
        if (finalizationStarted && finalizationFinished) {
            finalized = true;
            finalizationStarted = false;
            finalizationFinished = false;
            resetProducers();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process() throws Exception {
        int producersCount = producers.length;

        if (finalizationStarted) {
            finalizationFinished = processor.finalizeProcessor(pairOutputStream, processorContext);

            return !processOutputStream();
        } else if (this.pendingProducer != null) {
            return processProducer(this.pendingProducer);
        }

        return !scanProducers(producersCount);
    }

    private boolean scanProducers(int producersCount) throws Exception {
        int lastIdx = 0;
        boolean produced = false;

        //We should scan all producers if they were marked as closed
        int startFrom = startFrom();

        for (int idx = startFrom; idx < producersCount; idx++) {
            lastIdx = idx;
            ObjectProducer producer = this.producers[idx];

            Object[] inChunk = producer.produce();

            if ((JetUtil.isEmpty(inChunk)) || (producer.lastProducedCount() <= 0)) {
                continue;
            }

            produced = true;

            this.objectInputStream.consumeChunk(
                    inChunk,
                    producer.lastProducedCount()
            );

            if (!processProducer(producer)) {
                this.produced = true;
                nextProducerIdx = (idx + 1) % producersCount;
                return true;
            }
        }

        if ((!produced) && (producersWriteFinished)) {
            producingReadFinished = true;
        }

        if (producersCount > 0) {
            nextProducerIdx = (lastIdx + 1) % producersCount;
            this.produced = produced;
        } else {
            this.produced = false;
        }

        return false;
    }

    private int startFrom() {
        return producersWriteFinished ? 0 : nextProducerIdx;
    }

    private boolean processProducer(ObjectProducer producer) throws Exception {
        if (!processor.process(objectInputStream, pairOutputStream, producer.getName(), processorContext)) {
            pendingProducer = producer;
        } else {
            pendingProducer = null;
        }

        if (!processOutputStream()) {
            produced = true;
            return false;
        }

        pairOutputStream.reset();
        return pendingProducer == null;
    }


    private boolean processOutputStream() throws Exception {
        if (pairOutputStream.size() == 0) {
            checkFinalization();
            return true;
        } else {
            if (!onChunk(pairOutputStream)) {
                produced = true;
                return false;
            } else {
                checkFinalization();
                pairOutputStream.reset();
                return true;
            }
        }
    }

    @Override
    public boolean produced() {
        return produced;
    }


    @Override
    public boolean isFinalized() {
        return finalized;
    }

    @Override
    public void reset() {
        resetProducers();

        finalized = false;
        finalizationStarted = false;
        producersWriteFinished = false;
        producingReadFinished = false;
        pendingProducer = null;
    }

    @Override
    public void onOpen() {
        for (ObjectProducer producer : this.producers) {
            producer.open();
        }
        reset();
    }

    @Override
    public void onClose() {
        for (ObjectProducer producer : this.producers) {
            producer.close();
        }
    }

    @Override
    public void startFinalization() {
        finalizationStarted = true;
    }

    @Override
    public void onProducersWriteFinished() {
        producersWriteFinished = true;
    }

    @Override
    public boolean producersReadFinished() {
        return producingReadFinished;
    }

    private void resetProducers() {
        produced = false;
        nextProducerIdx = 0;
        pairOutputStream.reset();
        objectInputStream.reset();
    }

    @Override
    public boolean consumed() {
        return false;
    }

    @Override
    public void onReceiversClosed() {

    }
}
