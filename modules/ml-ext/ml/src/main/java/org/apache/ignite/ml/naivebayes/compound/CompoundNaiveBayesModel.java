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

package org.apache.ignite.ml.naivebayes.compound;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.ignite.ml.Exportable;
import org.apache.ignite.ml.Exporter;
import org.apache.ignite.ml.IgniteModel;
import org.apache.ignite.ml.environment.deploy.DeployableObject;
import org.apache.ignite.ml.inference.json.JSONModel;
import org.apache.ignite.ml.inference.json.JSONModelMixIn;
import org.apache.ignite.ml.inference.json.JSONWritable;
import org.apache.ignite.ml.inference.json.JacksonHelper;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.naivebayes.discrete.DiscreteNaiveBayesModel;
import org.apache.ignite.ml.naivebayes.gaussian.GaussianNaiveBayesModel;

/**
 * A compound Naive Bayes model which uses a composition of{@code GaussianNaiveBayesModel} and {@code
 * DiscreteNaiveBayesModel}.
 */
public class CompoundNaiveBayesModel implements IgniteModel<Vector, Double>, Exportable<CompoundNaiveBayesModel>,
    JSONWritable, DeployableObject {
    /** Serial version uid. */
    private static final long serialVersionUID = -5045925321135798960L;

    /** Prior probabilities of each class. */
    private double[] priorProbabilities;

    /** Labels. */
    private double[] labels;

    /** Gaussian Bayes model. */
    private GaussianNaiveBayesModel gaussianModel;

    /** Feature ids which should be skipped in Gaussian model. */
    private Collection<Integer> gaussianFeatureIdsToSkip = Collections.emptyList();

    /** Discrete Bayes model. */
    private DiscreteNaiveBayesModel discreteModel;

    /** Feature ids which should be skipped in Discrete model. */
    private Collection<Integer> discreteFeatureIdsToSkip = Collections.emptyList();

    /** */
    public CompoundNaiveBayesModel() {
    }

    /** {@inheritDoc} */
    @Override public <P> void saveModel(Exporter<CompoundNaiveBayesModel, P> exporter, P path) {
        exporter.save(this, path);
    }

    /** {@inheritDoc} */
    @Override public Double predict(Vector vector) {
        double[] probabilityPowers = new double[priorProbabilities.length];
        for (int i = 0; i < priorProbabilities.length; i++)
            probabilityPowers[i] = Math.log(priorProbabilities[i]);

        if (discreteModel != null)
            probabilityPowers = sum(probabilityPowers, discreteModel.probabilityPowers(skipFeatures(vector, discreteFeatureIdsToSkip)));

        if (gaussianModel != null)
            probabilityPowers = sum(probabilityPowers, gaussianModel.probabilityPowers(skipFeatures(vector, gaussianFeatureIdsToSkip)));

        int maxLbIdx = 0;
        for (int i = 0; i < probabilityPowers.length; i++) {
            if (probabilityPowers[i] > probabilityPowers[maxLbIdx])
                maxLbIdx = i;
        }
        return labels[maxLbIdx];
    }

    /** Returns a gaussian model. */
    public GaussianNaiveBayesModel getGaussianModel() {
        return gaussianModel;
    }

    /** Returns a discrete model. */
    public DiscreteNaiveBayesModel getDiscreteModel() {
        return discreteModel;
    }

    /** */
    public double[] getPriorProbabilities() {
        return priorProbabilities;
    }

    /** */
    public double[] getLabels() {
        return labels;
    }

    /** */
    public Collection<Integer> getGaussianFeatureIdsToSkip() {
        return gaussianFeatureIdsToSkip;
    }

    /** */
    public Collection<Integer> getDiscreteFeatureIdsToSkip() {
        return discreteFeatureIdsToSkip;
    }

    /** Sets prior probabilities. */
    public CompoundNaiveBayesModel withPriorProbabilities(double[] priorProbabilities) {
        this.priorProbabilities = priorProbabilities.clone();
        return this;
    }

    /** Sets labels. */
    public CompoundNaiveBayesModel withLabels(double[] labels) {
        this.labels = labels.clone();
        return this;
    }

    /** Sets a gaussian model. */
    public CompoundNaiveBayesModel withGaussianModel(GaussianNaiveBayesModel gaussianModel) {
        this.gaussianModel = gaussianModel;
        return this;
    }

    /** Sets a discrete model. */
    public CompoundNaiveBayesModel withDiscreteModel(DiscreteNaiveBayesModel discreteModel) {
        this.discreteModel = discreteModel;
        return this;
    }

    /** Sets feature ids to skip in Gaussian Bayes. */
    public CompoundNaiveBayesModel withGaussianFeatureIdsToSkip(Collection<Integer> gaussianFeatureIdsToSkip) {
        this.gaussianFeatureIdsToSkip = gaussianFeatureIdsToSkip;
        return this;
    }

    /** Sets feature ids to skip in discrete Bayes. */
    public CompoundNaiveBayesModel withDiscreteFeatureIdsToSkip(Collection<Integer> discreteFeatureIdsToSkip) {
        this.discreteFeatureIdsToSkip = discreteFeatureIdsToSkip;
        return this;
    }

    /** Returns index by index sum of two arrays. */
    private static double[] sum(double[] arr1, double[] arr2) {
        assert arr1.length == arr2.length;

        double[] result = new double[arr1.length];

        for (int i = 0; i < arr1.length; i++)
            result[i] = arr1[i] + arr2[i];

        return result;
    }

    /** Returns a new (shorter) vector without features provided in {@param featureIdsToSkip}. */
    private static Vector skipFeatures(Vector vector, Collection<Integer> featureIdsToSkip) {
        int newSize = vector.size() - featureIdsToSkip.size();
        double[] newFeaturesValues = new double[newSize];

        int idx = 0;
        for (int j = 0; j < vector.size(); j++) {
            if (featureIdsToSkip.contains(j)) continue;

            newFeaturesValues[idx] = vector.get(j);
            ++idx;
        }
        return VectorUtils.of(newFeaturesValues);
    }

    /** {@inheritDoc} */
    @JsonIgnore
    @Override public List<Object> getDependencies() {
        return Arrays.asList(discreteModel, gaussianModel);
    }

    /** {@inheritDoc} */
    @Override public void toJSON(Path path) {
        ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.addMixIn(CompoundNaiveBayesModel.class, JSONModelMixIn.class);

        ObjectWriter writer = mapper
            .writerFor(CompoundNaiveBayesModel.class)
            .withAttribute("formatVersion", JSONModel.JSON_MODEL_FORMAT_VERSION)
            .withAttribute("timestamp", System.currentTimeMillis())
            .withAttribute("uid", "dt_" + UUID.randomUUID().toString())
            .withAttribute("modelClass", CompoundNaiveBayesModel.class.getSimpleName());

        try {
            File file = new File(path.toAbsolutePath().toString());
            writer.writeValue(file, this);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Loads CompoundNaiveBayesModel from JSON file. */
    public static CompoundNaiveBayesModel fromJSON(Path path) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        CompoundNaiveBayesModel mdl;
        try {
            JacksonHelper.readAndValidateBasicJsonModelProperties(path, mapper, CompoundNaiveBayesModel.class.getSimpleName());
            mdl = mapper.readValue(new File(path.toAbsolutePath().toString()), CompoundNaiveBayesModel.class);
            return mdl;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
