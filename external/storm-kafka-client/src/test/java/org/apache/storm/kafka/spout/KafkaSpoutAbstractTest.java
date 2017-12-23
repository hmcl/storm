/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.storm.kafka.spout;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.storm.kafka.KafkaUnitRule;
import org.apache.storm.kafka.spout.builders.SingleTopicKafkaSpoutConfiguration;
import org.apache.storm.kafka.spout.internal.KafkaConsumerFactory;
import org.apache.storm.kafka.spout.internal.KafkaConsumerFactoryDefault;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Time;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public abstract class KafkaSpoutAbstractTest {
    @Rule
    public KafkaUnitRule kafkaUnitRule = new KafkaUnitRule();

    final TopologyContext topologyContext = mock(TopologyContext.class);
    final Map<String, Object> conf = new HashMap<>();
    final SpoutOutputCollector collector = mock(SpoutOutputCollector.class);
    final long commitOffsetPeriodMs = 2_000;

    KafkaConsumer<String, String> consumerSpy;
    KafkaSpout<String, String> spout;

    @Captor
    private ArgumentCaptor<Map<TopicPartition, OffsetAndMetadata>> commitCapture;
    private Time.SimulatedTime simulatedTime;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final KafkaSpoutConfig<String, String> spoutConfig = createSpoutConfig();

        consumerSpy = spy(new KafkaConsumerFactoryDefault<String, String>().createConsumer(spoutConfig));

        final KafkaConsumerFactory<String, String>  consumerFactory = new KafkaConsumerFactory<String, String>() {
            @Override
            public KafkaConsumer<String, String> createConsumer(KafkaSpoutConfig<String, String> kafkaSpoutConfig) {
                return consumerSpy;
            }

        };

        spout = new KafkaSpout<>(spoutConfig, consumerFactory);

        simulatedTime = new Time.SimulatedTime();
    }

    @After
    public void tearDown() throws Exception {
        simulatedTime.close();
    }

    abstract KafkaSpoutConfig<String, String> createSpoutConfig();

    void prepareSpout(int messageCount) throws Exception {
        SingleTopicKafkaUnitSetupHelper.populateTopicData(kafkaUnitRule.getKafkaUnit(), SingleTopicKafkaSpoutConfiguration.TOPIC, messageCount);
        SingleTopicKafkaUnitSetupHelper.initializeSpout(spout, conf, topologyContext, collector);
    }

    /**
     * Helper method to in sequence do:
     * <li>
     *     <ul>spout.nexTuple()</ul>
     *     <ul>verify messageId</ul>
     *     <ul>spout.ack(msgId)</ul>
     *     <ul>reset(collector) to be able to reuse mock</ul>
     * </li>
     *
     * @param offset offset of message to be verified
     * @return {@link ArgumentCaptor} of the messageId verified
     */
    ArgumentCaptor<Object> nextTuple_verifyEmitted_ack_resetCollectorMock(int offset) {
        spout.nextTuple();

        ArgumentCaptor<Object> messageId = verifyMessageEmitted(offset);

        spout.ack(messageId.getValue());

        reset(collector);

        return messageId;
    }

    // offset and messageId are used interchangeably
    ArgumentCaptor<Object> verifyMessageEmitted(int offset) {
        final ArgumentCaptor<Object> messageId = ArgumentCaptor.forClass(Object.class);

        verify(collector).emit(
            eq(SingleTopicKafkaSpoutConfiguration.STREAM),
            eq(new Values(SingleTopicKafkaSpoutConfiguration.TOPIC,
                Integer.toString(offset),
                Integer.toString(offset))),
            messageId.capture());

        return messageId;
    }

    void commitAndVerifyMessagesCommitted(long msgCount) {
        // reset commit timer such that commit happens on next call to nextTuple()
        Time.advanceTime(commitOffsetPeriodMs + KafkaSpout.TIMER_DELAY_MS);

        //Commit offsets
        spout.nextTuple();

        verifyAllMessagesCommitted(msgCount);
    }

    /*
     * Asserts that commitSync has been called once,
     * that there are only commits on one topic,
     * and that the committed offset covers messageCount messages
     */
    void verifyAllMessagesCommitted(long messageCount) {
        verify(consumerSpy).commitSync(commitCapture.capture());

        final Map<TopicPartition, OffsetAndMetadata> commits = commitCapture.getValue();
        assertThat("Expected commits for only one topic partition", commits.entrySet().size(), is(1));

        OffsetAndMetadata offset = commits.entrySet().iterator().next().getValue();
        assertThat("Expected committed offset to cover all emitted messages", offset.offset(), is(messageCount));

        reset(consumerSpy);
    }
}
