/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ml.math.primitives.vector.impl;

import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.ignite.ml.math.StorageConstants;
import org.apache.ignite.ml.math.primitives.matrix.Matrix;
import org.apache.ignite.ml.math.primitives.matrix.impl.SparseMatrix;
import org.apache.ignite.ml.math.primitives.vector.AbstractVector;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.storage.SparseVectorStorage;

/**
 * Local on-heap sparse vector based on hash map storage.
 */
public class SparseVector extends AbstractVector implements StorageConstants {
    /**
     *
     */
    public SparseVector() {
        // No-op.
    }

    /**
     * @param map Underlying map.
     * @param cp Should given map be copied.
     */
    public SparseVector(Map<Integer, Double> map, boolean cp) {
        setStorage(new SparseVectorStorage(map, cp));
    }

    /**
     * @param size Vector size.
     */
    public SparseVector(int size) {
        setStorage(new SparseVectorStorage(size));
    }

    /** */
    private SparseVectorStorage storage() {
        return (SparseVectorStorage)getStorage();
    }

    /** {@inheritDoc} */
    @Override public Vector like(int crd) {
        return new SparseVector(crd);
    }

    /** {@inheritDoc} */
    @Override public Matrix likeMatrix(int rows, int cols) {
        return new SparseMatrix(rows, cols);
    }

    /** {@inheritDoc} */
    @Override public Vector times(double x) {
        if (x == 0.0)
            return assign(0);
        else
            return super.times(x);
    }

    /** Indexes of non-default elements. */
    public IntSet indexes() {
        return storage().indexes();
    }

    /** {@inheritDoc} */
    @Override public Spliterator<Double> nonZeroSpliterator() {
        return new Spliterator<Double>() {
            /** {@inheritDoc} */
            @Override public boolean tryAdvance(Consumer<? super Double> act) {
                Set<Integer> indexes = storage().indexes();

                for (Integer idx : indexes)
                    act.accept(storageGet(idx));

                return true;
            }

            /** {@inheritDoc} */
            @Override public Spliterator<Double> trySplit() {
                return null; // No Splitting.
            }

            /** {@inheritDoc} */
            @Override public long estimateSize() {
                return storage().indexes().size();
            }

            /** {@inheritDoc} */
            @Override public int characteristics() {
                return ORDERED | SIZED;
            }
        };
    }
}
