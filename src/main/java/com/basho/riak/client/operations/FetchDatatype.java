/*
 * Copyright 2013 Basho Technologies Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.basho.riak.client.operations;

import com.basho.riak.client.cap.Quorum;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.operations.DtFetchOperation;
import com.basho.riak.client.operations.datatypes.DatatypeConverter;
import com.basho.riak.client.operations.datatypes.RiakCounter;
import com.basho.riak.client.operations.datatypes.RiakDatatype;
import com.basho.riak.client.operations.datatypes.RiakMap;
import com.basho.riak.client.operations.datatypes.RiakSet;
import com.basho.riak.client.query.crdt.types.CrdtElement;
import com.basho.riak.client.util.BinaryValue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class FetchDatatype<T extends RiakDatatype> extends RiakCommand<FetchDatatype.Response<T>>
{

    private final Location location;
    private final DatatypeConverter<T> converter;
    private final Map<DtFetchOption<?>, Object> options = new HashMap<DtFetchOption<?>, Object>();

    public FetchDatatype(Location location, DatatypeConverter<T> converter)
    {
        this.location = location;
        this.converter = converter;
        withOption(DtFetchOption.INCLUDE_CONTEXT, true);
    }

    public static FetchDatatype<RiakMap> fetchMap(Location key)
    {
        return new FetchDatatype<RiakMap>(key, DatatypeConverter.asMap());
    }

    public static FetchDatatype<RiakSet> fetchSet(Location key)
    {
        return new FetchDatatype<RiakSet>(key, DatatypeConverter.asSet());
    }

    public static FetchDatatype<RiakCounter> fetchCounter(Location key)
    {
        return new FetchDatatype<RiakCounter>(key, DatatypeConverter.asCounter());
    }

    public <U> FetchDatatype<T> withOption(DtFetchOption<U> option, U value)
    {
        options.put(option, value);
        return this;
    }

    @Override
    public Response<T> execute(RiakCluster cluster) throws ExecutionException, InterruptedException
    {
        DtFetchOperation.Builder builder = new DtFetchOperation.Builder(location.getBucket(), location.getKey());

        if (location.hasType())
        {
            builder.withBucketType(location.getType());
        }

        for (Map.Entry<DtFetchOption<?>, Object> entry : options.entrySet())
        {
            if (entry.getKey() == DtFetchOption.R)
            {
                builder.withR(((Quorum) entry.getValue()).getIntValue());
            }
            else if (entry.getKey() == DtFetchOption.PR)
            {
                builder.withPr(((Quorum) entry.getValue()).getIntValue());
            }
            else if (entry.getKey() == DtFetchOption.BASIC_QUORUM)
            {
                builder.withBasicQuorum((Boolean) entry.getValue());
            }
            else if (entry.getKey() == DtFetchOption.NOTFOUND_OK)
            {
                builder.withNotFoundOK((Boolean) entry.getValue());
            }
            else if (entry.getKey() == DtFetchOption.TIMEOUT)
            {
                builder.withTimeout((Integer) entry.getValue());
            }
            else if (entry.getKey() == DtFetchOption.SLOPPY_QUORUM)
            {
                builder.withSloppyQuorum((Boolean) entry.getValue());
            }
            else if (entry.getKey() == DtFetchOption.N_VAL)
            {
                builder.withNVal((Integer) entry.getValue());
            }
            else if (entry.getKey() == DtFetchOption.INCLUDE_CONTEXT)
            {
                builder.includeContext((Boolean) entry.getValue());
            }
        }

        DtFetchOperation operation = builder.build();

        DtFetchOperation.Response response = cluster.execute(operation).get();
        CrdtElement element = response.getCrdtElement();
        BinaryValue context = response.getContext();

        T datatype = converter.convert(element);

        return new Response<T>(datatype, context.getValue());

    }

    public static class Response<T extends RiakDatatype>
    {

        private final T datatype;
        private final byte[] context;

        public Response(T datatype, byte[] context)
        {
            this.datatype = datatype;
            this.context = context;
        }

        public T getDatatype()
        {
            return datatype;
        }

        public boolean hasContext()
        {
            return context != null;
        }

        public byte[] getContext()
        {
            return context;
        }
    }



}
