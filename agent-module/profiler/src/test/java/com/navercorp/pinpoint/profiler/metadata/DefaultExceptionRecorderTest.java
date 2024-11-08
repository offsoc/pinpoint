/*
 * Copyright 2023 NAVER Corp.
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
package com.navercorp.pinpoint.profiler.metadata;

import com.navercorp.pinpoint.common.util.CollectionUtils;
import com.navercorp.pinpoint.profiler.context.exception.DefaultExceptionRecorder;
import com.navercorp.pinpoint.profiler.context.exception.ExceptionRecorder;
import com.navercorp.pinpoint.profiler.context.exception.model.DefaultExceptionContext;
import com.navercorp.pinpoint.profiler.context.exception.model.ExceptionContext;
import com.navercorp.pinpoint.profiler.context.exception.model.ExceptionWrapper;
import com.navercorp.pinpoint.profiler.context.exception.model.ExceptionWrapperFactory;
import com.navercorp.pinpoint.profiler.context.exception.sampler.ExceptionChainSampler;
import com.navercorp.pinpoint.profiler.context.exception.storage.ExceptionStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author intr3p1d
 */
public class DefaultExceptionRecorderTest {

    private final static Logger logger = LogManager.getLogger(DefaultExceptionRecorderTest.class);

    ExceptionChainSampler exceptionChainSampler = new ExceptionChainSampler(1000);
    ExceptionWrapperFactory exceptionWrapperFactory = new ExceptionWrapperFactory(10, 1048);

    long START_TIME = 1;


    class TestExceptionStorage implements ExceptionStorage {

        List<ExceptionWrapper> wrappers;
        public List<ExceptionWrapper> outputStream;

        public TestExceptionStorage() {
            this.wrappers = new ArrayList<>();
            this.outputStream = new ArrayList<>();
        }

        @Override
        public void store(List<ExceptionWrapper> wrappers) {
            logger.error(wrappers);
            this.wrappers.addAll(wrappers);
        }

        @Override
        public void flush() {
            final List<ExceptionWrapper> copy = new ArrayList<>(wrappers);
            if (CollectionUtils.hasLength(copy)) {
                outputStream.addAll(copy);
            }
            wrappers.clear();
        }

        @Override
        public void close() {
        }

        public List<ExceptionWrapper> getWrappers() {
            return this.wrappers;
        }

        public List<ExceptionWrapper> getOutputStream() {
            return outputStream;
        }
    }

    private Function<Throwable, Throwable> getThrowableFunction(
            DefaultExceptionRecorder recorder,
            List<Throwable> throwable
    ) {
        return (Throwable th) -> {
            recorder.recordException(th, START_TIME);
            throwable.add(th);
            logger.info(th);
            return th;
        };
    }


    private List<ExceptionWrapper> newExceptionWrappers(Throwable throwable, long startTime, long exceptionId) {
        List<ExceptionWrapper> wrappers = new ArrayList<>();
        exceptionWrapperFactory.addAllExceptionWrappers(wrappers, throwable, null, startTime, exceptionId, 0);
        return wrappers;
    }

    @Test
    public void testRecordNothing() {
        List<Throwable> exceptions = new ArrayList<>();
        TestExceptionStorage exceptionStorage = new TestExceptionStorage();
        ExceptionContext context = new DefaultExceptionContext(exceptionStorage);
        DefaultExceptionRecorder exceptionRecorder = new DefaultExceptionRecorder(
                exceptionChainSampler, exceptionWrapperFactory, context
        );
        Function<Throwable, Throwable> throwableFunction = getThrowableFunction(
                exceptionRecorder, exceptions
        );

        exceptionRecorder.recordException(null, 0);
        exceptionRecorder.close();

        List<ExceptionWrapper> expected = new ArrayList<>();
        List<ExceptionWrapper> actual = exceptionStorage.getOutputStream();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testRecordException() {
        List<Throwable> exceptions = new ArrayList<>();
        TestExceptionStorage exceptionStorage = new TestExceptionStorage();
        ExceptionContext context = new DefaultExceptionContext(exceptionStorage);
        DefaultExceptionRecorder exceptionRecorder = new DefaultExceptionRecorder(
                exceptionChainSampler, exceptionWrapperFactory, context
        );
        Function<Throwable, Throwable> throwableFunction = getThrowableFunction(
                exceptionRecorder, exceptions
        );

        List<ExceptionWrapper> expected = null;
        List<ExceptionWrapper> actual = null;

        try {
            level3Error(throwableFunction);
        } catch (Throwable e) {
            expected = newExceptionWrappers(e, START_TIME, 1);
            exceptionRecorder.recordException(e, START_TIME);
            actual = exceptionStorage.getWrappers();
            Assertions.assertTrue(actual.isEmpty());
        }
        exceptionRecorder.close();
        actual = exceptionStorage.getOutputStream();
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testRecordNotChainedException() {
        List<Throwable> exceptions = new ArrayList<>();
        TestExceptionStorage exceptionStorage = new TestExceptionStorage();
        ExceptionContext context = new DefaultExceptionContext(exceptionStorage);
        DefaultExceptionRecorder exceptionRecorder = new DefaultExceptionRecorder(
                exceptionChainSampler, exceptionWrapperFactory, context
        );
        Function<Throwable, Throwable> throwableFunction = getThrowableFunction(
                exceptionRecorder, exceptions
        );

        List<ExceptionWrapper> expected1 = null;
        List<ExceptionWrapper> expected2 = null;
        List<ExceptionWrapper> actual1 = null;
        List<ExceptionWrapper> actual2 = null;

        Throwable throwable = null;

        try {
            notChainedException(throwableFunction);
        } catch (Throwable e) {
            expected1 = newExceptionWrappers(exceptions.get(exceptions.size() - 2), START_TIME, 1);
            exceptionRecorder.recordException(e, START_TIME);
            logger.warn(exceptionStorage.getWrappers());
            actual1 = new ArrayList<>(exceptionStorage.getWrappers());
            throwable = e;
            logger.warn(actual1);
            logger.warn(actual2);
            Assertions.assertFalse(actual1.isEmpty());
            Assertions.assertEquals(expected1, actual1);
        }


        exceptionStorage.flush();
        exceptionStorage.getOutputStream().clear();
        expected2 = newExceptionWrappers(throwable, START_TIME, 2);
        exceptionRecorder.close();
        actual2 = exceptionStorage.getOutputStream();
        Assertions.assertEquals(expected2, actual2);
    }

    @Test
    public void testRecordRethrowGivenException() {

        List<Throwable> exceptions = new ArrayList<>();
        TestExceptionStorage exceptionStorage = new TestExceptionStorage();
        ExceptionContext context = new DefaultExceptionContext(exceptionStorage);
        DefaultExceptionRecorder exceptionRecorder = new DefaultExceptionRecorder(
                exceptionChainSampler, exceptionWrapperFactory, context
        );
        Function<Throwable, Throwable> throwableFunction = getThrowableFunction(
                exceptionRecorder, exceptions
        );

        List<ExceptionWrapper> expected = null;
        List<ExceptionWrapper> actual = null;

        try {
            rethrowGivenException(throwableFunction);
        } catch (Throwable e) {
            expected = newExceptionWrappers(e, START_TIME, 1);
            exceptionRecorder.recordException(e, START_TIME);
            actual = exceptionStorage.getWrappers();
            Assertions.assertTrue(actual.isEmpty());
        }

        exceptionRecorder.close();
        actual = exceptionStorage.getOutputStream();
        Assertions.assertEquals(expected, actual);
    }


    public void notChainedException(Function<Throwable, Throwable> interceptor) throws Throwable {
        try {
            level3Error(interceptor);
        } catch (Throwable e) {
            throw interceptor.apply(new RuntimeException("Not Chained, Another New Exception"));
        }
    }

    public void rethrowGivenException(Function<Throwable, Throwable> interceptor) throws Throwable {
        try {
            level3Error(interceptor);
        } catch (Exception e) {
            throw interceptor.apply(e);
        }
    }

    public void level3Error(Function<Throwable, Throwable> interceptor) throws Throwable {
        try {
            level2Error(interceptor);
        } catch (Throwable e) {
            throw interceptor.apply(new RuntimeException("Level 3 Error", e));
        }
    }

    public void level2Error(Function<Throwable, Throwable> interceptor) throws Throwable {
        try {
            level1Error(interceptor);
        } catch (Throwable e) {
            throw interceptor.apply(new RuntimeException("Level 2 Error", e));
        }
    }

    public void level1Error(Function<Throwable, Throwable> interceptor) throws Throwable {
        throw interceptor.apply(new RuntimeException("Level 1 Error"));
    }

}
