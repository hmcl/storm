/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.storm.kinesis.spout;

import java.util.List;

import org.apache.storm.tuple.Fields;

import com.amazonaws.services.kinesis.model.Record;

/**
 * Used to convert Kinesis record into a tuple.
 */
public interface IKinesisRecordScheme extends java.io.Serializable {

    /**
     * @param record Kinesis record
     * @return List of values (to be emitted as a tuple)
     */
    List<Object> deserialize(Record record);

    /**
     * @return output fields
     */
    Fields getOutputFields();

}